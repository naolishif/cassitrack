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

    // ─────────────────────────────
    // GET ALL USERS
    // ─────────────────────────────

    public List<User> getAllUsers() {
        log.info("Fetching all users");
        return userRepository.findAll();
    }

    // ─────────────────────────────
    // REGISTER USER (Auth flow)
    // ─────────────────────────────

    public User registerUser(RegisterRequest req) {

        if (req.getEmail() == null || req.getPassword() == null ||
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
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .role(req.getRole() != null ? req.getRole().toUpperCase() : "TRAVELLER")
                //.telephone(req.getTelephone())
                .build();

        User saved = userRepository.save(user);
        log.info("User registered: {}", saved.getEmail());
        return saved;
    }

    // ─────────────────────────────
    // CREATE USER (Admin flow)
    // ─────────────────────────────

    public User createUser(User user) {

        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }

        if (userRepository.existsByEmail(user.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        //if (userRepository.existsByTelephone(user.getTelephone())) {
            //throw new IllegalArgumentException("Telephone already exists");
        //}

        if (userRepository.existsByTaxId(user.getTaxId())) {
            throw new IllegalArgumentException("Tax ID already exists");
        }

        user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));

        User saved = userRepository.save(user);
        log.info("User created: {}", saved.getEmail());
        return saved;
    }

    // ─────────────────────────────
    // UPDATE USER
    // ─────────────────────────────

    public User updateUser(Long id, User updatedUser) {

        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!user.getEmail().equals(updatedUser.getEmail()) &&
                userRepository.existsByEmail(updatedUser.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        //if (!user.getTelephone().equals(updatedUser.getTelephone()) &&
          //      userRepository.existsByTelephone(updatedUser.getTelephone())) {
          //  throw new IllegalArgumentException("Telephone already exists");
        //}

        if (!user.getTaxId().equals(updatedUser.getTaxId()) &&
                userRepository.existsByTaxId(updatedUser.getTaxId())) {
            throw new IllegalArgumentException("Tax ID already exists");
        }

        user.setTaxId(updatedUser.getTaxId());
        user.setName(updatedUser.getName());
        user.setSurname(updatedUser.getSurname());
        user.setEmail(updatedUser.getEmail());
        //user.setTelephone(updatedUser.getTelephone());
        user.setRole(updatedUser.getRole());

        if (updatedUser.getPasswordHash() != null &&
                !updatedUser.getPasswordHash().isBlank()) {
            user.setPasswordHash(
                    passwordEncoder.encode(updatedUser.getPasswordHash())
            );
        }

        User saved = userRepository.save(user);
        log.info("User updated: {}", saved.getEmail());
        return saved;
    }

    // ─────────────────────────────
    // DELETE USER
    // ─────────────────────────────

    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new IllegalArgumentException("User not found");
        }
        userRepository.deleteById(id);
        log.info("User deleted: id={}", id);
    }

    // ─────────────────────────────
    // GET USER BY EMAIL
    // ─────────────────────────────

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }
}
