package it.unicas.cassitrack.controller;

import io.swagger.v3.oas.annotations.Operation;
import it.unicas.cassitrack.dto.AuthResponse;
import it.unicas.cassitrack.dto.LoginRequest;
import it.unicas.cassitrack.dto.LoginResponse;
import it.unicas.cassitrack.dto.RegisterRequest;
import it.unicas.cassitrack.model.User;
import it.unicas.cassitrack.service.UserService;
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

    @PostMapping("/register")
    @Operation(summary = "Register a new user account")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {

        try {
            // FIXED: We now delegate the registration to registerUser(req) which matches perfectly!
            User saved = userService.registerUser(req);
            log.info("New user registered successfully via public flow: {}", saved.getEmail());

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(AuthResponse.builder()
                            .token("MOCK_TOKEN_UNTIL_LOGIN")
                            .email(saved.getEmail())
                            .name(saved.getName())
                            .role(saved.getRole())
                            .expiresInMs(86400000L)
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
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest) {

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getEmail(),
                            loginRequest.getPassword()
                    )
            );

            String token = jwtUtil.generateToken(authentication);
            User user = userService.getUserByEmail(loginRequest.getEmail());

            LoginResponse response = new LoginResponse();
            response.setToken(token);
            response.setUsername(user.getEmail());
            response.setEmail(user.getEmail());
            response.setRole(user.getRole());

            return ResponseEntity.ok(response);

        } catch (AuthenticationException e) {
            return ResponseEntity.badRequest().body("Username or password incorrect!!");
        }
    }
}