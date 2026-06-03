package it.unicas.cassitrack.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Internal automatic ID for the database

    @NotBlank(message = "Tax ID is required")
    @Column(unique = true, nullable = false, name = "tax_id")
    private String taxId; // National Identity Number / Codice Fiscale

    @NotBlank(message = "Name is required")
    @Column(nullable = false)
    private String name; // First name

    @NotBlank(message = "Surname is required")
    @Column(nullable = false)
    private String surname; // Last name

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email")
    @Column(unique = true, nullable = false)
    private String email; // Login email identifier


    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "password_hash", nullable = false)
    private String passwordHash; // Encrypted password

    @NotBlank(message = "Role is required")
    @Column(nullable = false)
    private String role; // User permission level (ADMIN, DRIVER, etc.)

    //@Column(name = "telephone", nullable = true)
    //private String telephone; // Contact telephone number
}