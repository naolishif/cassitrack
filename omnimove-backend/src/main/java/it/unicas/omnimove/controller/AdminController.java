package it.unicas.omnimove.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.unicas.omnimove.dto.AdminCreateUserRequest;
import it.unicas.omnimove.dto.UserDTO;
import it.unicas.omnimove.model.User;
import it.unicas.omnimove.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin", description = "User management — ADMIN role required")
public class AdminController {

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;

    // ── guard helper ──────────────────────────────────────────────────────
    private boolean isAdmin(UserDetails principal) {
        if (principal == null) return false;
        return userRepo.findByEmail(principal.getUsername())
                .map(u -> "ADMIN".equalsIgnoreCase(u.getRole()))
                .orElse(false);
    }

    private UserDTO toDTO(User u) {
        return UserDTO.builder()
                .id(u.getId())
                .name(u.getName())
                .email(u.getEmail())
                .role(u.getRole())
                .build();
    }

    // ── GET /api/v1/admin/users ───────────────────────────────────────────
    @GetMapping("/users")
    @Operation(summary = "List all users", description = "Returns all registered users. ADMIN only.")
    public ResponseEntity<?> listUsers(
            @AuthenticationPrincipal UserDetails principal) {

        if (!isAdmin(principal))
            return ResponseEntity.status(403)
                    .body(Map.of("message", "Forbidden: ADMIN role required"));

        List<UserDTO> users = userRepo.findAll()
                .stream().map(this::toDTO)
                .collect(Collectors.toList());

        log.info("Admin {} fetched user list ({} users)", principal.getUsername(), users.size());
        return ResponseEntity.ok(users);
    }

    // ── POST /api/v1/admin/users ──────────────────────────────────────────
    @PostMapping("/users")
    @Operation(summary = "Create a new user", description = "Creates user with any role. ADMIN only.")
    public ResponseEntity<?> createUser(
            @RequestBody AdminCreateUserRequest req,
            @AuthenticationPrincipal UserDetails principal) {

        if (!isAdmin(principal))
            return ResponseEntity.status(403)
                    .body(Map.of("message", "Forbidden: ADMIN role required"));

        if (req.getName() == null || req.getEmail() == null || req.getPassword() == null)
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "name, email and password are required"));

        if (userRepo.existsByEmail(req.getEmail()))
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Email already registered"));

        String role = (req.getRole() != null &&
                       req.getRole().equalsIgnoreCase("ADMIN")) ? "ADMIN" : "TRAVELLER";

        User user = User.builder()
                .name(req.getName())
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .role(role)
                .build();
        userRepo.save(user);

        log.info("Admin {} created user: {} ({})", principal.getUsername(), req.getEmail(), role);
        return ResponseEntity.ok(toDTO(user));
    }

    // ── DELETE /api/v1/admin/users/{id} ──────────────────────────────────
    @DeleteMapping("/users/{id}")
    @Operation(summary = "Delete a user by ID", description = "ADMIN only. Cannot delete yourself.")
    public ResponseEntity<?> deleteUser(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails principal) {

        if (!isAdmin(principal))
            return ResponseEntity.status(403)
                    .body(Map.of("message", "Forbidden: ADMIN role required"));

        return userRepo.findById(id).map(target -> {
            // Impedisce all'admin di cancellare se stesso
            if (target.getEmail().equalsIgnoreCase(principal.getUsername()))
                return ResponseEntity.badRequest()
                        .<Object>body(Map.of("message", "Cannot delete your own account"));

            userRepo.delete(target);
            log.warn("Admin {} deleted user {} ({})", principal.getUsername(), id, target.getEmail());
            return ResponseEntity.ok().<Object>body(Map.of("message", "User deleted", "id", id));
        }).orElse(ResponseEntity.notFound().<Object>build());
    }

    // ── GET /api/v1/admin/users/stats ─────────────────────────────────────
    @GetMapping("/users/stats")
    @Operation(summary = "User counts by role", description = "Returns total, admins, travellers. ADMIN only.")
    public ResponseEntity<?> userStats(
            @AuthenticationPrincipal UserDetails principal) {

        if (!isAdmin(principal))
            return ResponseEntity.status(403)
                    .body(Map.of("message", "Forbidden: ADMIN role required"));

        List<User> all = userRepo.findAll();
        long total     = all.size();
        long admins    = all.stream().filter(u -> "ADMIN".equalsIgnoreCase(u.getRole())).count();
        long travellers= all.stream().filter(u -> "TRAVELLER".equalsIgnoreCase(u.getRole())).count();

        return ResponseEntity.ok(Map.of(
                "total",      total,
                "admins",     admins,
                "travellers", travellers
        ));
    }
}
