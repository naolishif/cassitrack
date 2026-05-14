package it.unicas.omnimove.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
@Data @Builder
public class AuthResponse {
    private String token;
    private String email;
    private String name;
    private String role;
    @JsonProperty("expires_in_ms")
    private long expiresInMs;
    private String message;
}
