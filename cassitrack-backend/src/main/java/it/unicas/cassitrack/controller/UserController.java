package it.unicas.cassitrack.controller;

import io.swagger.v3.oas.annotations.Operation;
import it.unicas.cassitrack.dto.RegisterRequest;
import it.unicas.cassitrack.dto.UserDTO;
import it.unicas.cassitrack.model.User;
import it.unicas.cassitrack.service.UserService;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@CrossOrigin
@PreAuthorize("hasAnyAuthority('ADMIN', 'ROLE_ADMIN')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // ─────────────────────────────────────────────────────────────────
    // GET ALL USERS
    // ─────────────────────────────────────────────────────────────────
    @GetMapping
    @Operation(summary = "Get all users to populate the admin dashboard")
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        log.info("REST request to get all users");
        return ResponseEntity.ok(userService.getAllUsers());
    }

    // ─────────────────────────────────────────────────────────────────
    // CREATE USER (MAPPED WITH REGISTER_REQUEST DTO FOR VALIDATION)
    // ─────────────────────────────────────────────────────────────────
    @PostMapping
    @Operation(summary = "Create a new user verifying strong password rules")
    public ResponseEntity<User> createUser(
            @Valid @RequestBody RegisterRequest registerRequest
    ) {
        log.info("REST request to create user with email: {}", registerRequest.getEmail());

        // We pass the validated DTO to the service layer
        User createdUser = userService.createUser(registerRequest);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(createdUser);
    }

    // ─────────────────────────────────────────────────────────────────
    // UPDATE USER (MAPPED WITH REGISTER_REQUEST DTO)
    // ─────────────────────────────────────────────────────────────────
    @PutMapping("/{id}")
    @Operation(summary = "Update an existing user verifying password optional rules")
    public ResponseEntity<User> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody RegisterRequest registerRequest
    ) {
        log.info("REST request to update user ID: {}", id);

        User updatedUser = userService.updateUser(id, registerRequest);
        return ResponseEntity.ok(updatedUser);
    }

    // ─────────────────────────────────────────────────────────────────
    // DELETE USER
    // ─────────────────────────────────────────────────────────────────
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a user from the system")
    public ResponseEntity<Void> deleteUser(
            @PathVariable Long id
    ) {
        log.info("REST request to delete user ID: {}", id);
        userService.deleteUser(id);
        return ResponseEntity.noContent().build(); // 204 No Content
    }

    // ─────────────────────────────────────────────────────────────────
    // ERROR HANDLER
    // ─────────────────────────────────────────────────────────────────
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(
            IllegalArgumentException ex
    ) {
        log.warn("Bad request exception caught: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ex.getMessage());
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<String> handleSecurityException(SecurityException ex) {
        log.warn("Authorization denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
    }
}