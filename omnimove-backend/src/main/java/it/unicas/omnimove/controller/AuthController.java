package it.unicas.omnimove.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.unicas.omnimove.dto.*;
import it.unicas.omnimove.model.User;
import it.unicas.omnimove.repository.UserRepository;
import it.unicas.omnimove.security.JwtUtil;
import it.unicas.omnimove.service.EmailService;
import it.unicas.omnimove.service.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Register, login, email verification, password reset")
public class AuthController {

    private static final int MAX_FAILED_ATTEMPTS = 2;
    private static final int VERIFY_EXPIRY_HOURS = 24;
    private static final int RESET_EXPIRY_HOURS  = 1;

    private final UserRepository    userRepo;
    private final PasswordEncoder   passwordEncoder;
    private final JwtUtil           jwtUtil;
    private final EmailService      emailService;
    private final RateLimiterService rateLimiter;

    // ── REGISTER ────────────────────────────────────────────────────

    @PostMapping("/register")
    @Operation(summary = "Register a new passenger account")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest req,
                                                  HttpServletRequest request) {
        // Rate limit: 5 registrations per IP per hour
        if (!rateLimiter.allowRegister(getClientIp(request)))
            return tooManyRequests("Too many registration attempts. Please try again later.");

        if (req.getName() == null || req.getEmail() == null || req.getPassword() == null)
            return ResponseEntity.badRequest()
                    .body(AuthResponse.builder().message("Name, email and password are required").build());

        if (!req.getPassword().equals(req.getConfirmPassword()))
            return ResponseEntity.badRequest()
                    .body(AuthResponse.builder().message("Passwords do not match").build());

        if (!isPasswordValid(req.getPassword()))
            return ResponseEntity.badRequest()
                    .body(AuthResponse.builder()
                            .message("Password must be at least 8 characters and include an uppercase letter, a lowercase letter, a number, and a special character (@$!%*?&_#).")
                            .build());

        if (userRepo.existsByEmail(req.getEmail()))
            return ResponseEntity.badRequest()
                    .body(AuthResponse.builder().message("Email already registered").build());

        String verificationToken = UUID.randomUUID().toString();

        User user = User.builder()
                .name(req.getName())
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .role("TRAVELLER")
                .verified(false)
                .verificationToken(verificationToken)
                .verificationTokenExpiry(LocalDateTime.now().plusHours(VERIFY_EXPIRY_HOURS))
                .failedLoginAttempts(0)
                .build();
        userRepo.save(user);

        emailService.sendVerificationEmail(req.getEmail(), verificationToken);
        log.info("New user registered (unverified): {}", req.getEmail());

        return ResponseEntity.ok(AuthResponse.builder()
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole())
                .message("Registration successful! Please check your email to verify your account.")
                .build());
    }

    // ── LOGIN ────────────────────────────────────────────────────────

    @PostMapping("/login")
    @Operation(summary = "Login with email and password")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest req) {

        var userOpt = userRepo.findByEmail(req.getEmail());
        if (userOpt.isEmpty())
            return ResponseEntity.status(401)
                    .body(AuthResponse.builder().message("Invalid email or password").build());

        User user = userOpt.get();

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
            userRepo.save(user);

            boolean suggestReset = user.getFailedLoginAttempts() >= MAX_FAILED_ATTEMPTS;
            log.warn("Failed login attempt #{} for {}", user.getFailedLoginAttempts(), req.getEmail());

            return ResponseEntity.status(401)
                    .body(AuthResponse.builder()
                            .message("Invalid email or password")
                            .suggestPasswordReset(suggestReset ? Boolean.TRUE : null)
                            .build());
        }

        if (!user.isVerified())
            return ResponseEntity.status(403)
                    .body(AuthResponse.builder()
                            .message("Please verify your email address before logging in. Check your inbox.")
                            .build());

        user.setFailedLoginAttempts(0);
        userRepo.save(user);

        String token = jwtUtil.generateToken(user.getEmail());
        log.info("User logged in: {}", req.getEmail());

        return ResponseEntity.ok(AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole())
                .expiresInMs(86400000L)
                .message("Login successful")
                .build());
    }

    // ── EMAIL VERIFICATION ───────────────────────────────────────────

    @GetMapping("/verify")
    @Operation(summary = "Verify email address via link sent by email")
    public void verifyEmail(@RequestParam String token, HttpServletResponse response) throws IOException {

        var userOpt = userRepo.findByVerificationToken(token);
        if (userOpt.isEmpty()) {
            response.sendRedirect("/omnimove-login.html?verified=invalid");
            return;
        }

        User user = userOpt.get();
        if (user.getVerificationTokenExpiry() != null
                && LocalDateTime.now().isAfter(user.getVerificationTokenExpiry())) {
            response.sendRedirect("/omnimove-login.html?verified=expired");
            return;
        }

        user.setVerified(true);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiry(null);
        userRepo.save(user);

        log.info("Email verified for: {}", user.getEmail());
        response.sendRedirect("/omnimove-login.html?verified=true");
    }

    // ── RESEND VERIFICATION ──────────────────────────────────────────

    @PostMapping("/resend-verification")
    @Operation(summary = "Resend the email verification link")
    public ResponseEntity<AuthResponse> resendVerification(@RequestBody LoginRequest req) {

        // Rate limit: 3 resends per email per hour
        if (!rateLimiter.allowResendVerification(req.getEmail()))
            return tooManyRequests("Too many resend attempts. Please wait before requesting another link.");

        var userOpt = userRepo.findByEmail(req.getEmail());
        if (userOpt.isEmpty() || userOpt.get().isVerified())
            return ResponseEntity.ok(AuthResponse.builder()
                    .message("If that email exists and is unverified, a new link has been sent.")
                    .build());

        User user = userOpt.get();
        String newToken = UUID.randomUUID().toString();
        user.setVerificationToken(newToken);
        user.setVerificationTokenExpiry(LocalDateTime.now().plusHours(VERIFY_EXPIRY_HOURS));
        userRepo.save(user);

        emailService.sendVerificationEmail(user.getEmail(), newToken);
        log.info("Verification email resent to: {}", user.getEmail());

        return ResponseEntity.ok(AuthResponse.builder()
                .message("Verification email resent. Please check your inbox.")
                .build());
    }

    // ── FORGOT PASSWORD ──────────────────────────────────────────────

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password reset link via email")
    public ResponseEntity<AuthResponse> forgotPassword(@RequestBody LoginRequest req) {

        // Rate limit: 3 requests per email per hour
        if (!rateLimiter.allowForgotPassword(req.getEmail()))
            return tooManyRequests("Too many password reset attempts. Please wait before requesting another link.");

        var userOpt = userRepo.findByEmail(req.getEmail());
        if (userOpt.isPresent() && userOpt.get().isVerified()) {
            User user = userOpt.get();
            String resetToken = UUID.randomUUID().toString();
            user.setResetPasswordToken(resetToken);
            user.setResetPasswordTokenExpiry(LocalDateTime.now().plusHours(RESET_EXPIRY_HOURS));
            userRepo.save(user);
            emailService.sendPasswordResetEmail(user.getEmail(), resetToken);
            log.info("Password reset email sent to: {}", req.getEmail());
        }

        // Always return the same message to prevent email enumeration
        return ResponseEntity.ok(AuthResponse.builder()
                .message("If that email is registered, you will receive a reset link shortly.")
                .build());
    }

    // ── RESET PASSWORD ───────────────────────────────────────────────

    @PostMapping("/reset-password")
    @Operation(summary = "Set a new password using the reset token")
    public ResponseEntity<AuthResponse> resetPassword(@RequestBody ResetPasswordRequest req) {

        if (req.getToken() == null || req.getNewPassword() == null)
            return ResponseEntity.badRequest()
                    .body(AuthResponse.builder().message("Token and new password are required").build());

        if (!isPasswordValid(req.getNewPassword()))
            return ResponseEntity.badRequest()
                    .body(AuthResponse.builder()
                            .message("Password must be at least 8 characters and include an uppercase letter, a lowercase letter, a number, and a special character.")
                            .build());

        if (!req.getNewPassword().equals(req.getConfirmPassword()))
            return ResponseEntity.badRequest()
                    .body(AuthResponse.builder().message("Passwords do not match").build());

        var userOpt = userRepo.findByResetPasswordToken(req.getToken());
        if (userOpt.isEmpty())
            return ResponseEntity.badRequest()
                    .body(AuthResponse.builder().message("Invalid or expired reset link").build());

        User user = userOpt.get();
        if (user.getResetPasswordTokenExpiry() != null
                && LocalDateTime.now().isAfter(user.getResetPasswordTokenExpiry()))
            return ResponseEntity.badRequest()
                    .body(AuthResponse.builder().message("Reset link has expired. Please request a new one.").build());

        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        user.setResetPasswordToken(null);
        user.setResetPasswordTokenExpiry(null);
        user.setFailedLoginAttempts(0);
        userRepo.save(user);

        log.info("Password reset successful for: {}", user.getEmail());

        return ResponseEntity.ok(AuthResponse.builder()
                .message("Password updated successfully! You can now log in.")
                .build());
    }

    // ── RESET PAGE (email link lands here) ──────────────────────────
    /**
     * The reset-password link in emails points here instead of to omnimove-login.html.
     * This endpoint embeds the token directly in the returned HTML so it is never
     * dependent on query-string parameters surviving the email client / safe-link proxy.
     *
     * Flow:
     *   Email link → GET /api/v1/auth/reset-page?token=UUID
     *   → backend validates token, returns tiny HTML
     *   → that HTML sets sessionStorage and redirects to /omnimove-login.html
     *   → login page init() finds token in sessionStorage → shows reset form
     */
    @GetMapping(value = "/reset-page", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> resetPage(@RequestParam(required = false) String token) {

        // Sanitise: UUID tokens only contain hex chars and dashes
        String safeToken = (token != null) ? token.replaceAll("[^a-zA-Z0-9\\-]", "") : "";

        if (safeToken.isBlank()) {
            return htmlRedirect("/omnimove-login.html");
        }

        var userOpt = userRepo.findByResetPasswordToken(safeToken);
        boolean valid = userOpt.isPresent() && (
            userOpt.get().getResetPasswordTokenExpiry() == null ||
            !LocalDateTime.now().isAfter(userOpt.get().getResetPasswordTokenExpiry())
        );

        if (!valid) {
            // Token unknown or expired — store an error message and redirect to login
            return ResponseEntity.ok(
                "<!DOCTYPE html><html><head><meta charset='UTF-8'><script>" +
                "sessionStorage.setItem('omnimove_flash_err','Reset link is invalid or has expired. Please request a new one.');" +
                "window.location.replace('/omnimove-login.html');" +
                "</script></head><body></body></html>");
        }

        // Valid token — embed it in sessionStorage so the login page can read it
        // even after URL params have been stripped by the email client
        return ResponseEntity.ok(
            "<!DOCTYPE html><html><head><meta charset='UTF-8'><script>" +
            "sessionStorage.setItem('omnimove_pending_reset','" + safeToken + "');" +
            "window.location.replace('/omnimove-login.html');" +
            "</script></head><body></body></html>");
    }

    private ResponseEntity<String> htmlRedirect(String url) {
        return ResponseEntity.ok(
            "<!DOCTYPE html><html><head><script>" +
            "window.location.replace('" + url + "');" +
            "</script></head><body></body></html>");
    }

    // ── CURRENT USER ─────────────────────────────────────────────────

    @GetMapping("/me")
    @Operation(summary = "Get current user profile")
    public ResponseEntity<AuthResponse> me(
            @org.springframework.security.core.annotation.AuthenticationPrincipal
            org.springframework.security.core.userdetails.UserDetails userDetails) {

        if (userDetails == null) return ResponseEntity.status(401).build();

        return userRepo.findByEmail(userDetails.getUsername())
                .map(u -> ResponseEntity.ok(AuthResponse.builder()
                        .email(u.getEmail()).name(u.getName()).role(u.getRole()).build()))
                .orElse(ResponseEntity.notFound().build());
    }

    // ── DELETE ACCOUNT ────────────────────────────────────────────────

    @DeleteMapping("/account")
    @Operation(summary = "Permanently delete the authenticated user's account")
    public ResponseEntity<AuthResponse> deleteAccount(
            @org.springframework.security.core.annotation.AuthenticationPrincipal
            org.springframework.security.core.userdetails.UserDetails userDetails) {

        if (userDetails == null) return ResponseEntity.status(401).build();

        return userRepo.findByEmail(userDetails.getUsername())
                .map(u -> {
                    userRepo.delete(u);
                    log.info("Account deleted: {}", u.getEmail());
                    return ResponseEntity.ok(AuthResponse.builder()
                            .message("Account deleted successfully.").build());
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── HELPERS ──────────────────────────────────────────────────────

    /**
     * Extracts the real client IP, respecting X-Forwarded-For set by a reverse proxy.
     */
    /**
     * Password must have ≥8 chars, 1 uppercase, 1 lowercase, 1 digit, 1 special char.
     */
    private boolean isPasswordValid(String password) {
        if (password == null || password.length() < 8) return false;
        return password.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&_#]).{8,}$");
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim(); // take first IP in chain
        }
        return request.getRemoteAddr();
    }

    private ResponseEntity<AuthResponse> tooManyRequests(String message) {
        return ResponseEntity.status(429)
                .body(AuthResponse.builder().message(message).build());
    }
}
