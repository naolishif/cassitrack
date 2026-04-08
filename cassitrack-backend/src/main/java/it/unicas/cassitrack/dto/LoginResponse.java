package it.unicas.cassitrack.dto;

import lombok.Data;

@Data
public class LoginResponse {
    private String token;
    private String username;
    private String email;
    private String role;
}
