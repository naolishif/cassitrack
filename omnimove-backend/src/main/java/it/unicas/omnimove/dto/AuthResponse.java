package it.unicas.omnimove.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {
    private String token;
    private String email;
    private String name;
    private String role;
    @JsonProperty("expires_in_ms")
    private long expiresInMs;
    private Long id;
    private String message;

    /** true → el frontend muestra el botón "Olvidé mi contraseña" */
    @JsonProperty("suggest_password_reset")
    private Boolean suggestPasswordReset;
}
