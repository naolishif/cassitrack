package it.unicas.cassitrack.dto;

import lombok.Data;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Data
public class RegisterRequest {

    @NotBlank(message = "Tax ID is required")
    @Size(max = 50, message = "Tax ID cannot exceed 50 characters")
    private String taxId;

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name cannot exceed 100 characters")
    private String name;

    @NotBlank(message = "Surname is required")
    @Size(max = 100, message = "Surname cannot exceed 100 characters")
    private String surname;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 200, message = "Email cannot exceed 200 characters")
    private String email;

    // MATCHES USER.JAVA & HTML PAYLOAD PERFECTLY Now!
    @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*[0-9])(?=.*[!@#$%^&*(),.?\":{}|<>_+\\-*\\/]).{8,}$",
            message = "Password must be at least 8 characters long, including an uppercase letter, a lowercase letter, a number, and a special character."
    )
    private String passwordHash;

    @NotBlank(message = "Telephone is required")
    @Size(max = 20, message = "Telephone cannot exceed 20 characters")
    private String telephone;

    @NotBlank(message = "Role is required")
    private String role;
}