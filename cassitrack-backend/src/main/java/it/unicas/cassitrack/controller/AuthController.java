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
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getUsername(),
                            loginRequest.getPassword()
                    )
            );

            String token = jwtUtil.generateToken(authentication);

            // Update last login
            User user = userRepository.findByUsername(loginRequest.getUsername()).orElseThrow();
            user.setLastLogin(LocalDateTime.now());
            userRepository.save(user);

            LoginResponse response = new LoginResponse();
            response.setToken(token);
            response.setUsername(user.getUsername());
            response.setEmail(user.getEmail());
            response.setRole(user.getRole());

            return ResponseEntity.ok(response);

        } catch (AuthenticationException e) {
            return ResponseEntity.badRequest().body("Invalid username or password");
        }
    }
}
