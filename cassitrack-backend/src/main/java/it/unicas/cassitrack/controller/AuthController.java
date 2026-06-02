package it.unicas.cassitrack.controller;

import io.swagger.v3.oas.annotations.Operation;
import it.unicas.cassitrack.dto.AuthResponse;
import it.unicas.cassitrack.dto.LoginRequest;
import it.unicas.cassitrack.dto.LoginResponse;
import it.unicas.cassitrack.dto.RegisterRequest;
import it.unicas.cassitrack.model.User;
import it.unicas.cassitrack.repository.UserRepository;
import it.unicas.cassitrack.security.JwtUtil;
import lombok.extern.slf4j.Slf4j; // <--- AGGIUNTO PER I LOG
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder; // <--- AGGIUNTO PER SIKUREZZA
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@Slf4j // <--- AGGIUNTO PER ABILITARE L'OGGETTO log
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository; // Il nome corretto è questo

    @Autowired
    private PasswordEncoder passwordEncoder; // <--- INIETTATO PER CRIPTARE LA PASSWORD

    @PostMapping("/register")
    @Operation(summary = "Register a new user account")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest req) {

        // 1. Validazione dei campi obbligatori per il DB di Cassitrack
        if (req.getEmail() == null || req.getPassword() == null ||
                req.getName() == null || req.getSurname() == null || req.getTaxId() == null) {

            return ResponseEntity.badRequest()
                    .body(AuthResponse.builder()
                            .message("Tax ID, Name, Surname, Email and Password are required.")
                            .build());
        }

        // 2. Controllo duplicati (Corretto in userRepository)
        if (userRepository.existsByEmail(req.getEmail())) {
            return ResponseEntity.badRequest()
                    .body(AuthResponse.builder()
                            .message("Email already registered.")
                            .build());
        }

        // 3. Mappatura ed encoding della password (Usa passwordEncoder così il login funzionerà!)
        User user = User.builder()
                .taxId(req.getTaxId())
                .name(req.getName())
                .surname(req.getSurname())
                .email(req.getEmail())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                // Se il ruolo è presente nella richiesta usa quello (in MAIUSCOLO), altrimenti usa "TRAVELLER"
                .role(req.getRole() != null ? req.getRole().toUpperCase() : "TRAVELLER")
                //.telephone(req.getTelephone())
                .build();

        userRepository.save(user); // Corretto in userRepository
        log.info("New user registered successfully in Cassitrack: {}", req.getEmail());

        // 4. Generazione del Token
        // Nota: Se il tuo jwtUtil richiede un oggetto Authentication (come nel login),
        // conviene generare un token finto o configurare jwtUtil per accettare anche solo la stringa email.
        String token = "MOCK_TOKEN_UNTIL_LOGIN";

        return ResponseEntity.ok(AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole())
                .expiresInMs(86400000L) // 24 ore
                .message("Registration successful")
                .build());
    }

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