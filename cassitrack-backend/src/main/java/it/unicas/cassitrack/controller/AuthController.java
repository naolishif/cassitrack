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
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest req) {
        try {
            // 1. Delegate business logic and saving to UserService
            User registeredUser = userService.registerUser(req);

            // 2. Generate a REAL token using the newly created user's email
            String token = jwtUtil.generateTokenFromUsername(registeredUser.getEmail());

            // 3. Return the response
            return ResponseEntity.ok(AuthResponse.builder()
                    .token(token)
                    .email(registeredUser.getEmail())
                    .name(registeredUser.getName())
                    .role(registeredUser.getRole())
                    .expiresInMs(86400000L) // 24 hours
                    .message("Registration successful")
                    .build());

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(AuthResponse.builder()
                            .message(e.getMessage())
                            .build());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest) {
        // ... (Keep your existing login logic, but change userRepository to userService if you prefer)
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getEmail(),
                            loginRequest.getPassword()
                    )
            );

            String token = jwtUtil.generateToken(authentication);

            // It's cleaner to use the service here too!
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