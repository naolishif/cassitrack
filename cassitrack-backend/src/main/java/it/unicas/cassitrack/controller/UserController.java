package it.unicas.cassitrack.controller;

import it.unicas.cassitrack.model.User;
import it.unicas.cassitrack.service.UserService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@CrossOrigin
public class UserController {

    private final UserService userService;

    public UserController(
            UserService userService
    ) {
        this.userService = userService;
    }

    // ─────────────────────────────
    // GET ALL USERS
    // ─────────────────────────────

    @GetMapping
    public List<User> getAllUsers() {

        return userService.getAllUsers();
    }

    // ─────────────────────────────
    // CREATE USER
    // ─────────────────────────────

    @PostMapping
    public User createUser(
            @RequestBody User user
    ) {

        return userService.createUser(user);
    }

    // ─────────────────────────────
    // UPDATE USER
    // ─────────────────────────────

    @PutMapping("/{id}")
    public User updateUser(
            @PathVariable Long id,
            @Valid @RequestBody User updatedUser
    ) {

        return userService.updateUser(id, updatedUser);
    }

    // ─────────────────────────────
    // DELETE USER
    // ─────────────────────────────

    @DeleteMapping("/{id}")
    public void deleteUser(
            @PathVariable Long id
    ) {

        userService.deleteUser(id);
    }

    // ─────────────────────────────
    // ERROR HANDLER
    // ─────────────────────────────

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleIllegalArgument(
            IllegalArgumentException ex
    ) {

        return ex.getMessage();
    }
}