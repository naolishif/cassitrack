package it.unicas.cassitrack.controller;

import io.swagger.v3.oas.annotations.Operation;
import it.unicas.cassitrack.dto.AuthResponse;
import it.unicas.cassitrack.dto.LoginRequest;
import it.unicas.cassitrack.dto.LoginResponse;
import it.unicas.cassitrack.dto.RegisterRequest;
import it.unicas.cassitrack.model.User;
import it.unicas.cassitrack.service.LoginAttemptService;
import it.unicas.cassitrack.service.SecurityAuditService;
import it.unicas.cassitrack.service.TokenBlacklistService;
import it.unicas.cassitrack.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import it.unicas.cassitrack.security.JwtUtil;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserService userService;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @Autowired
    private SecurityAuditService securityAuditService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user account")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req,
                                                  HttpServletRequest request) {

        try {
            User saved = userService.registerUser(req);
            log.info("New user registered successfully via public flow: {}", saved.getEmail());
            securityAuditService.registration(saved.getEmail(), getClientIp(request));

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(AuthResponse.builder()
                            .token("MOCK_TOKEN_UNTIL_LOGIN")
                            .email(saved.getEmail())
                            .name(saved.getName())
                            .role(saved.getRole())
                            .expiresInMs(3600000L)
                            .message("Registration successful")
                            .build());

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(AuthResponse.builder()
                            .message(e.getMessage())
                            .build());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // LOGIN ACCOUNT
    // ─────────────────────────────────────────────────────────────────
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest,
                                   HttpServletRequest request) {

        String email = loginRequest.getEmail();

        if (loginAttemptService.isBlocked(email)) {
            securityAuditService.accountLocked(email);
            return ResponseEntity.status(429)
                .body("Too many failed login attempts. Please wait 15 minutes and try again.");
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, loginRequest.getPassword())
            );

            loginAttemptService.resetAttempts(email);
            String token = jwtUtil.generateToken(authentication);
            User user = userService.getUserByEmail(email);
            securityAuditService.loginSuccess(email, getClientIp(request));

            LoginResponse response = new LoginResponse();
            response.setToken(token);
            response.setUsername(user.getEmail());
            response.setEmail(user.getEmail());
            response.setRole(user.getRole());

            return ResponseEntity.ok(response);

        } catch (AuthenticationException e) {
            loginAttemptService.recordFailure(email);
            securityAuditService.loginFailure(email, getClientIp(request));
            return ResponseEntity.badRequest().body("Username or password incorrect!!");
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout and invalidate the current JWT token")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            long remaining = jwtUtil.getRemainingValidityMs(token);
            if (remaining > 0) {
                String email = jwtUtil.getUsernameFromToken(token);
                tokenBlacklistService.blacklist(token, remaining);
                log.info("Token revoked on logout");
                securityAuditService.logout(email);
            }
        }
        return ResponseEntity.noContent().build();
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For may be a comma-separated list; first value is the original client
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) return realIp.trim();
        return request.getRemoteAddr();
    }
}