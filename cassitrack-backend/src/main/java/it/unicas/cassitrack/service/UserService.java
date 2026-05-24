package it.unicas.cassitrack.service;

import it.unicas.cassitrack.model.User;
import it.unicas.cassitrack.repository.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

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

        return userRepository.findAll();
    }

    // ─────────────────────────────
    // CREATE USER
    // ─────────────────────────────

    public User createUser(User user) {

        if (
                user.getPasswordHash() == null ||
                        user.getPasswordHash().isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Password is required"
            );
        }

        // EMAIL
        if (userRepository.existsByEmail(user.getEmail())) {

            throw new IllegalArgumentException(
                    "Email already exists"
            );
        }

        // TELEPHONE
        if (userRepository.existsByTelephone(user.getTelephone())) {

            throw new IllegalArgumentException(
                    "Telephone already exists"
            );
        }

        // TAX ID
        if (userRepository.existsByTaxId(user.getTaxId())) {

            throw new IllegalArgumentException(
                    "Tax ID already exists"
            );
        }

        user.setPasswordHash(
                passwordEncoder.encode(user.getPasswordHash())
        );

        return userRepository.save(user);
    }

    // ─────────────────────────────
    // UPDATE USER
    // ─────────────────────────────

    public User updateUser(Long id, User updatedUser) {

        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "User not found"
                        )
                );

        // EMAIL
        if (
                !user.getEmail().equals(updatedUser.getEmail()) &&
                        userRepository.existsByEmail(updatedUser.getEmail())
        ) {

            throw new IllegalArgumentException(
                    "Email already exists"
            );
        }

        // TELEPHONE
        if (
                !user.getTelephone().equals(updatedUser.getTelephone()) &&
                        userRepository.existsByTelephone(updatedUser.getTelephone())
        ) {

            throw new IllegalArgumentException(
                    "Telephone already exists"
            );
        }

        // TAX ID
        if (
                !user.getTaxId().equals(updatedUser.getTaxId()) &&
                        userRepository.existsByTaxId(updatedUser.getTaxId())
        ) {

            throw new IllegalArgumentException(
                    "Tax ID already exists"
            );
        }

        // UPDATE FIELDS
        user.setTaxId(updatedUser.getTaxId());
        user.setName(updatedUser.getName());
        user.setSurname(updatedUser.getSurname());
        user.setEmail(updatedUser.getEmail());
        user.setTelephone(updatedUser.getTelephone());
        user.setRole(updatedUser.getRole());

        // PASSWORD
        if (
                updatedUser.getPasswordHash() != null &&
                        !updatedUser.getPasswordHash().isBlank()
        ) {

            user.setPasswordHash(
                    passwordEncoder.encode(
                            updatedUser.getPasswordHash()
                    )
            );
        }

        return userRepository.save(user);
    }

    // ─────────────────────────────
    // DELETE USER
    // ─────────────────────────────

    public void deleteUser(Long id) {

        userRepository.deleteById(id);
    }
}