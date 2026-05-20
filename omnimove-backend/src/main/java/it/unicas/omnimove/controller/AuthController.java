package it.unicas.omnimove.controller;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.unicas.omnimove.dto.*;
import it.unicas.omnimove.model.User;
import it.unicas.omnimove.repository.UserRepository;
import it.unicas.omnimove.security.JwtUtil;
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

    @PostMapping("/register")
    @Operation(summary="Register a new passenger account",
        description="Creates account with name, email and password. Returns JWT token.")
    public ResponseEntity<AuthResponse> register(
            @RequestBody RegisterRequest req) {

        if (req.getEmail()==null||req.getPassword()==null||req.getName()==null)
            return ResponseEntity.badRequest()
                .body(AuthResponse.builder().message("Name, email and password are required").build());

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
            .expiresInMs(86400000L)
            .message("Registration successful").build());
    }

    @PostMapping("/login")
    @Operation(summary="Login with email and password",
        description="Returns JWT token valid for 24 hours.")
    public ResponseEntity<AuthResponse> login(
            @RequestBody LoginRequest req) {

        return userRepo.findByEmail(req.getEmail())
            .filter(u -> passwordEncoder.matches(req.getPassword(), u.getPassword()))
            .map(u -> {
                String token = jwtUtil.generateToken(u.getEmail());
                log.info("Passenger logged in: {}", req.getEmail());
                return ResponseEntity.ok(AuthResponse.builder()
                    .token(token).email(u.getEmail())
                    .name(u.getName()).role(u.getRole())
                    .expiresInMs(86400000L)
                    .message("Login successful").build());
            })
            .orElse(ResponseEntity.status(401)
                .body(AuthResponse.builder().message("Invalid email or password").build()));
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
