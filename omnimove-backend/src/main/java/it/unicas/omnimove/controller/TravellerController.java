package it.unicas.omnimove.controller;

import it.unicas.omnimove.dto.TravellerUpdateRequest;
import it.unicas.omnimove.model.User;
import it.unicas.omnimove.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/traveller")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Traveller", description = "Traveller self-service profile management")
public class TravellerController {

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;

    @PutMapping("/me")
    @Operation(summary = "Update own profile")
    public ResponseEntity<?> updateMe(
            @RequestBody TravellerUpdateRequest req,
            @AuthenticationPrincipal UserDetails principal) {

        if (principal == null)
            return ResponseEntity.status(401).body(Map.of("message", "Not authenticated"));

        User user = userRepo.findByEmail(principal.getUsername())
                .orElse(null);
        if (user == null)
            return ResponseEntity.notFound().build();

        if (req.getName() != null && !req.getName().isBlank())
            user.setName(req.getName());

        if (req.getEmail() != null && !req.getEmail().isBlank()
                && !req.getEmail().equalsIgnoreCase(user.getEmail())) {
            if (userRepo.existsByEmail(req.getEmail()))
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Email already in use"));
            user.setEmail(req.getEmail());
        }

        if (req.getPassword() != null && !req.getPassword().isBlank())
            user.setPassword(passwordEncoder.encode(req.getPassword()));

        userRepo.save(user);
        log.info("Traveller {} updated their profile", principal.getUsername());
        return ResponseEntity.ok(Map.of("message", "Profile updated successfully"));
    }
}
