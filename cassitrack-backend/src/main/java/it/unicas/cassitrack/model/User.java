package it.unicas.cassitrack.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    // ─────────────────────────────
    // ID
    // ─────────────────────────────

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ─────────────────────────────
    // TAX ID
    // ─────────────────────────────

    @NotBlank(message = "Tax ID is required")
    @Column(unique = true, nullable = false, name = "tax_id")
    private String taxId;

    // ─────────────────────────────
    // NAME
    // ─────────────────────────────

    @NotBlank(message = "Name is required")
    @Column(nullable = false)
    private String name;

    // ─────────────────────────────
    // SURNAME
    // ─────────────────────────────

    @NotBlank(message = "Surname is required")
    @Column(nullable = false)
    private String surname;

    // ─────────────────────────────
    // EMAIL
    // ─────────────────────────────

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email")
    @Column(unique = true, nullable = false)
    private String email;

    // ─────────────────────────────
    // PASSWORD
    // ─────────────────────────────


    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    // ─────────────────────────────
    // ROLE
    // ─────────────────────────────

    @NotBlank(message = "Role is required")
    @Column(nullable = false)
    private String role;

    // ─────────────────────────────
    // TELEPHONE
    // ─────────────────────────────

    @Column(name = "telephone")
    private String telephone;
}