package it.unicas.omnimove.controller;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.unicas.omnimove.dto.*;
import it.unicas.omnimove.model.User;
import it.unicas.omnimove.repository.UserRepository;
import it.unicas.omnimove.security.JwtUtil;
import it.unicas.omnimove.service.LoginAttemptService;
import it.unicas.omnimove.service.TokenBlacklistService;
import it.unicas.omnimove.service.EmailService;
import it.unicas.omnimove.service.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final LoginAttemptService loginAttemptService;
    private final TokenBlacklistService tokenBlacklistService;

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
        //if (!isPasswordStrong(req.getPassword()))
          //  return ResponseEntity.badRequest()
            //        .body(AuthResponse.builder()
              //              .message("Password must be at least 8 characters with uppercase, lowercase, a number, and a special character.")
                //            .build());

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
                .expiresInMs(3600000L)
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
     * Returns a complete, self-contained HTML page with the reset form.
     * The token is embedded directly in the page — no redirects, no URL params,
     * no sessionStorage. The browser renders this page as-is.
     */
    @GetMapping("/reset-page")
    public void resetPage(@RequestParam(required = false) String token,
                          HttpServletResponse response) throws IOException {

        response.setContentType("text/html;charset=UTF-8");
        String safeToken = (token != null) ? token.replaceAll("[^a-zA-Z0-9\\-]", "") : "";

        if (safeToken.isBlank()) {
            response.sendRedirect("/omnimove-login.html");
            return;
        }

        var userOpt = userRepo.findByResetPasswordToken(safeToken);
        boolean valid = userOpt.isPresent() && (
            userOpt.get().getResetPasswordTokenExpiry() == null ||
            !LocalDateTime.now().isAfter(userOpt.get().getResetPasswordTokenExpiry())
        );

        if (!valid) {
            response.getWriter().write(resetPageHtml(safeToken, true));
            return;
        }

        response.getWriter().write(resetPageHtml(safeToken, false));
    }

    @PostMapping("/logout")
    @Operation(summary="Logout and invalidate the current JWT token")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            long remaining = jwtUtil.getRemainingValidityMs(token);
            if (remaining > 0) {
                tokenBlacklistService.blacklist(token, remaining);
                log.info("Token revoked on logout");
            }
        }
        return ResponseEntity.noContent().build();
    }

    private boolean isPasswordStrong(String pw) {
        return pw != null
                && pw.length() >= 8
                && pw.chars().anyMatch(Character::isUpperCase)
                && pw.chars().anyMatch(Character::isLowerCase)
                && pw.chars().anyMatch(Character::isDigit)
                && pw.chars().anyMatch(c -> "!@#$%^&*(),.?\":{}|<>_+-*/".indexOf(c) >= 0);
    }

    private String resetPageHtml(String token, boolean expired) {
        String msg = expired
            ? "<div class='msg err'>This reset link has expired or is invalid. "
              + "<a href='/omnimove-login.html' style='color:#f87171'>Back to login</a></div>"
            : "";
        String form = expired ? "" :
            "<form onsubmit='doReset(event)'>" +
            "<div class='field'><label>New Password</label>" +
            "<input id='p1' type='password' placeholder='Min 8 characters'/>" +
            "<span class='ferr' id='e1'></span></div>" +
            "<div class='field'><label>Confirm Password</label>" +
            "<input id='p2' type='password' placeholder='Repeat password'/>" +
            "<span class='ferr' id='e2'></span></div>" +
            "<button type='submit' id='btn'>Set New Password</button>" +
            "</form>";

        return "<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8'/>" +
            "<meta name='viewport' content='width=device-width,initial-scale=1'/>" +
            "<title>OMNIMOVE — Reset Password</title>" +
            "<link href='https://fonts.googleapis.com/css2?family=DM+Mono:wght@400;500&family=Syne:wght@700;800&display=swap' rel='stylesheet'/>" +
            "<style>" +
            "*{box-sizing:border-box;margin:0;padding:0}" +
            ":root{--bg:#07090F;--panel:#0F1623;--border:#1A2744;--accent:#3B82F6;--red:#EF4444;--green:#22C55E;--text:#E2E8F0;--dim:#4B5563;--mono:'DM Mono',monospace;--display:'Syne',sans-serif}" +
            "body{background:var(--bg);color:var(--text);font-family:var(--display);min-height:100vh;display:flex;align-items:center;justify-content:center;padding:20px}" +
            ".box{width:100%;max-width:400px}" +
            ".logo{font-size:32px;font-weight:800;text-align:center;margin-bottom:6px}" +
            ".logo span{color:var(--accent)}" +
            ".tag{font-family:var(--mono);font-size:11px;color:var(--dim);text-align:center;margin-bottom:32px}" +
            ".card{background:var(--panel);border:1px solid var(--border);border-radius:12px;padding:28px;display:flex;flex-direction:column;gap:16px}" +
            ".title{font-size:16px;font-weight:800;margin-bottom:4px}" +
            ".sub{font-family:var(--mono);font-size:11px;color:var(--dim);line-height:1.5}" +
            ".field{display:flex;flex-direction:column;gap:4px}" +
            "label{font-family:var(--mono);font-size:10px;color:var(--dim);text-transform:uppercase;letter-spacing:1.5px}" +
            "input{padding:11px 14px;background:#141E2E;color:var(--text);border:1px solid var(--border);border-radius:8px;font-family:var(--mono);font-size:13px;outline:none;width:100%}" +
            "input:focus{border-color:var(--accent)}" +
            "input.err{border-color:var(--red)}" +
            ".ferr{font-family:var(--mono);font-size:10px;color:var(--red);display:none}" +
            ".ferr.show{display:block}" +
            "button{padding:13px;background:var(--accent);color:#fff;border:none;border-radius:8px;font-family:var(--mono);font-size:13px;font-weight:700;cursor:pointer;width:100%}" +
            "button:disabled{opacity:.5;cursor:not-allowed}" +
            ".msg{font-family:var(--mono);font-size:11px;text-align:center;padding:10px 12px;border-radius:6px;line-height:1.5}" +
            ".msg.ok{background:rgba(34,197,94,.1);color:var(--green)}" +
            ".msg.err{background:rgba(239,68,68,.1);color:var(--red)}" +
            "#msgBox:empty{display:none}" +
            "form{display:flex;flex-direction:column;gap:12px}" +
            "</style></head><body>" +
            "<div class='box'>" +
            "<div class='logo'>OMNI<span>MOVE</span></div>" +
            "<div class='tag'>Smart mobility for Cassino — UNICAS 2025/2026</div>" +
            "<div class='card'>" +
            "<div><div class='title'>Reset your password</div>" +
            "<div class='sub'>Choose a new password for your account.</div></div>" +
            "<div id='msgBox'></div>" +
            msg + form +
            "</div></div>" +
            "<script>" +
            "const TOKEN='" + token + "';" +
            "function valid(p){return /^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&_#]).{8,}$/.test(p)}" +
            "function showErr(id,msg){const e=document.getElementById(id);e.textContent=msg;e.className='ferr show';}" +
            "function clearErr(id){const e=document.getElementById(id);e.textContent='';e.className='ferr';}" +
            "async function doReset(e){" +
            "e.preventDefault();" +
            "const p1=document.getElementById('p1').value;" +
            "const p2=document.getElementById('p2').value;" +
            "clearErr('e1');clearErr('e2');" +
            "let ok=true;" +
            "if(!p1){showErr('e1','⚠ Required');ok=false;}" +
            "else if(!valid(p1)){showErr('e1','⚠ Min 8 chars: uppercase, lowercase, number & symbol (@$!%*?&_#)');ok=false;}" +
            "if(!p2){showErr('e2','⚠ Required');ok=false;}" +
            "else if(p1!==p2){showErr('e2','⚠ Passwords do not match');ok=false;}" +
            "if(!ok)return;" +
            "const btn=document.getElementById('btn');" +
            "btn.disabled=true;btn.textContent='Saving…';" +
            "try{" +
            "const r=await fetch('/api/v1/auth/reset-password',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({token:TOKEN,newPassword:p1,confirmPassword:p2})});" +
            "const d=await r.json();" +
            "const box=document.getElementById('msgBox');" +
            "if(r.ok){box.innerHTML=\"<div class='msg ok'>&#x2713; Password updated! <a href='/omnimove-login.html' style='color:#22C55E'>Sign in</a></div>\";document.querySelector('form').style.display='none';}" +
            "else{box.innerHTML=\"<div class='msg err'>\"+(d.message||'Reset failed')+\"</div>\";btn.disabled=false;btn.textContent='Set New Password';}" +
            "}catch(ex){console.error(ex);document.getElementById('msgBox').innerHTML=\"<div class='msg err'>Cannot reach server.</div>\";btn.disabled=false;btn.textContent='Set New Password';}" +
            "}" +
            "document.getElementById('p2').addEventListener('input',function(){" +
            "const p1=document.getElementById('p1').value;" +
            "if(this.value&&p1&&this.value!==p1)showErr('e2','⚠ Passwords do not match');" +
            "else clearErr('e2');" +
            "});" +
            "</script></body></html>";
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
