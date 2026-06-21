package it.unicas.omnimove.controller;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.unicas.omnimove.dto.*;
import it.unicas.omnimove.model.User;
import it.unicas.omnimove.repository.UserRepository;
import it.unicas.omnimove.security.JwtUtil;
import it.unicas.omnimove.service.LoginAttemptService;
import it.unicas.omnimove.service.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor @Slf4j
@Tag(name="Authentication", description="Passenger register and login")
public class AuthController {

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final LoginAttemptService loginAttemptService;
    private final TokenBlacklistService tokenBlacklistService;

    @PostMapping("/register")
    @Operation(summary="Register a new passenger account",
        description="Creates account with name, email and password. Returns JWT token.")
    public ResponseEntity<AuthResponse> register(
            @RequestBody RegisterRequest req) {

        if (req.getEmail()==null||req.getPassword()==null||req.getName()==null)
            return ResponseEntity.badRequest()
                .body(AuthResponse.builder().message("Name, email and password are required").build());

        if (!isPasswordStrong(req.getPassword()))
            return ResponseEntity.badRequest()
                .body(AuthResponse.builder()
                    .message("Password must be at least 8 characters with uppercase, lowercase, a number, and a special character.")
                    .build());

        if (userRepo.existsByEmail(req.getEmail()))
            return ResponseEntity.badRequest()
                .body(AuthResponse.builder().message("Email already registered").build());

        User user = User.builder()
            .name(req.getName())
            .email(req.getEmail())
            .password(passwordEncoder.encode(req.getPassword()))
            .role("TRAVELLER")
            .build();
        userRepo.save(user);

        String token = jwtUtil.generateToken(user.getEmail());
        log.info("New passenger registered: {}", req.getEmail());

        return ResponseEntity.ok(AuthResponse.builder()
            .token(token).email(user.getEmail())
            .name(user.getName()).role(user.getRole())
            .expiresInMs(3600000L)
            .message("Registration successful").build());
    }

    @PostMapping("/login")
    @Operation(summary="Login with email and password",
        description="Returns JWT token valid for 1 hour. Blocked after 100 consecutive failures.")
    public ResponseEntity<AuthResponse> login(
            @RequestBody LoginRequest req) {

        String email = req.getEmail();

        if (loginAttemptService.isBlocked(email)) {
            return ResponseEntity.status(429)
                .body(AuthResponse.builder()
                    .message("Too many failed login attempts. Please wait 15 minutes and try again.")
                    .build());
        }

        return userRepo.findByEmail(email)
            .filter(u -> passwordEncoder.matches(req.getPassword(), u.getPassword()))
            .map(u -> {
                loginAttemptService.resetAttempts(email);
                String token = jwtUtil.generateToken(u.getEmail());
                log.info("Passenger logged in: {}", email);
                return ResponseEntity.ok(AuthResponse.builder()
                    .token(token).email(u.getEmail())
                    .name(u.getName()).role(u.getRole())
                    .expiresInMs(3600000L)
                    .message("Login successful").build());
            })
            .orElseGet(() -> {
                loginAttemptService.recordFailure(email);
                return ResponseEntity.status(401)
                    .body(AuthResponse.builder().message("Invalid email or password").build());
            });
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

    @GetMapping("/me")
    @Operation(summary="Get current user profile")
    public ResponseEntity<AuthResponse> me(
            @org.springframework.security.core.annotation.AuthenticationPrincipal
            org.springframework.security.core.userdetails.UserDetails userDetails) {
        if (userDetails == null)
            return ResponseEntity.status(401).build();
        return userRepo.findByEmail(userDetails.getUsername())
            .map(u -> ResponseEntity.ok(AuthResponse.builder()
                .email(u.getEmail()).name(u.getName()).role(u.getRole()).build()))
            .orElse(ResponseEntity.notFound().build());
    }
}
