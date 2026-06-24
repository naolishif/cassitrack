package it.unicas.omnimove.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = "[^<>\"']*", message = "Name contains invalid characters")
    private String name;

    @NotBlank
    @Email
    @Size(max = 150)
    private String email;

    private String password;
    private String confirmPassword;
}
