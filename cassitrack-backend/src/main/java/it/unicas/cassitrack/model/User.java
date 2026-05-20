package it.unicas.cassitrack.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Internal automatic ID for the database

    @Column(unique = true, nullable = false, name = "tax_id")
    private String taxId; // National Identity Number / Codice Fiscale

    @Column(nullable = false)
    private String name; // First name

    @Column(nullable = false)
    private String surname; // Last name

    @Column(unique = true, nullable = false)
    private String email; // Login email identifier

    @Column(name = "password_hash", nullable = false)
    private String passwordHash; // Encrypted password

    @Column(nullable = false)
    private String role; // User permission level (ADMIN, DRIVER, etc.)

    @Column(name = "telephone", nullable = true)
    private String telephone; // Contact telephone number
}