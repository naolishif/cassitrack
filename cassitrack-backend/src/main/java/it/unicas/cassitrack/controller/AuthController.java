package it.unicas.cassitrack.controller;

import it.unicas.cassitrack.dto.LoginRequest;
import it.unicas.cassitrack.dto.LoginResponse;
import it.unicas.cassitrack.model.User;
import it.unicas.cassitrack.repository.UserRepository;
import it.unicas.cassitrack.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            // Pasamos las credenciales directamente al AuthenticationManager de Spring Security
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getEmail(),
                            loginRequest.getPassword()
                    )
            );

            String token = jwtUtil.generateToken(authentication);
            User user = userRepository.findByEmail(loginRequest.getEmail()).orElseThrow(
                    () -> new RuntimeException("User not found after authentication")
            );

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