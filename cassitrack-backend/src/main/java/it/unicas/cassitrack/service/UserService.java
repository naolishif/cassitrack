package it.unicas.cassitrack.service;

import it.unicas.cassitrack.dto.RegisterRequest;
import it.unicas.cassitrack.model.User;
import it.unicas.cassitrack.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ─────────────────────────────────────────────────────────────────
    // GET ALL USERS
    // ─────────────────────────────────────────────────────────────────
    public List<User> getAllUsers() {
        log.info("Fetching all users");
        return userRepository.findAll();
    }

    // ─────────────────────────────────────────────────────────────────
    // REGISTER USER (Auth flow - Public registration)
    // ─────────────────────────────────────────────────────────────────
    public User registerUser(RegisterRequest req) {

        if (req.getEmail() == null || req.getPasswordHash() == null ||
                req.getName() == null || req.getSurname() == null ||
                req.getTaxId() == null) {
            throw new IllegalArgumentException(
                    "Tax ID, Name, Surname, Email and Password are required."
            );
        }

        if (userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("Email already registered.");
        }

        if (req.getTelephone() != null &&
                userRepository.existsByTelephone(req.getTelephone())) {
            throw new IllegalArgumentException("Telephone already registered.");
        }

        if (userRepository.existsByTaxId(req.getTaxId())) {
            throw new IllegalArgumentException("Tax ID already registered.");
        }

        User user = User.builder()
                .taxId(req.getTaxId())
                .name(req.getName())
                .surname(req.getSurname())
                .email(req.getEmail())
                .passwordHash(passwordEncoder.encode(req.getPasswordHash()))
                .role(req.getRole() != null ? req.getRole().toUpperCase() : "TRAVELLER")
                .telephone(req.getTelephone())
                .build();

        User saved = userRepository.save(user);
        log.info("User registered: {}", saved.getEmail());
        return saved;
    }

    // ─────────────────────────────────────────────────────────────────
    // CREATE USER (Admin flow - Receives the validated DTO)
    // ─────────────────────────────────────────────────────────────────
    public User createUser(RegisterRequest req) {

        if (req.getPasswordHash() == null || req.getPasswordHash().isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }

        if (userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        if (userRepository.existsByTelephone(req.getTelephone())) {
            throw new IllegalArgumentException("Telephone already exists");
        }

        if (userRepository.existsByTaxId(req.getTaxId())) {
            throw new IllegalArgumentException("Tax ID already exists");
        }

        // We convert the safe DTO data into a database User entity
        User user = User.builder()
                .taxId(req.getTaxId())
                .name(req.getName())
                .surname(req.getSurname())
                .email(req.getEmail())
                .passwordHash(passwordEncoder.encode(req.getPasswordHash())) // Hashing raw text!
                .role(req.getRole() != null ? req.getRole().toUpperCase() : "DRIVER")
                .telephone(req.getTelephone())
                .build();

        User saved = userRepository.save(user);
        log.info("User created by admin: {}", saved.getEmail());
        return saved;
    }

    // ─────────────────────────────────────────────────────────────────
    // UPDATE USER (Admin flow - Handles optional password change)
    // ─────────────────────────────────────────────────────────────────
    public User updateUser(Long id, RegisterRequest req) {

        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!user.getEmail().equals(req.getEmail()) &&
                userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        if (user.getTelephone() != null && !user.getTelephone().equals(req.getTelephone()) &&
                userRepository.existsByTelephone(req.getTelephone())) {
            throw new IllegalArgumentException("Telephone already exists");
        }

        if (!user.getTaxId().equals(req.getTaxId()) &&
                userRepository.existsByTaxId(req.getTaxId())) {
            throw new IllegalArgumentException("Tax ID already exists");
        }

        // Map updated fields from DTO to entity
        user.setTaxId(req.getTaxId());
        user.setName(req.getName());
        user.setSurname(req.getSurname());
        user.setEmail(req.getEmail());
        user.setTelephone(req.getTelephone());
        user.setRole(req.getRole() != null ? req.getRole().toUpperCase() : user.getRole());

        // Only update and re-hash password if the admin typed a new one in the modal
        if (req.getPasswordHash() != null && !req.getPasswordHash().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(req.getPasswordHash()));
        }

        User saved = userRepository.save(user);
        log.info("User updated by admin: {}", saved.getEmail());
        return saved;
    }

    // ─────────────────────────────────────────────────────────────────
    // DELETE USER
    // ─────────────────────────────────────────────────────────────────
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new IllegalArgumentException("User not found");
        }
        userRepository.deleteById(id);
        log.info("User deleted: id={}", id);
    }

    // ─────────────────────────────────────────────────────────────────
    // GET USER BY EMAIL
    // ─────────────────────────────────────────────────────────────────
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }
}