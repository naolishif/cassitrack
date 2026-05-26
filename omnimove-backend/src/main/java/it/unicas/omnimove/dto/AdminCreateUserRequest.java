package it.unicas.omnimove.dto;

import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor
public class AdminCreateUserRequest {
    private String name;
    private String email;
    private String password;
    private String role;   // TRAVELLER | ADMIN
}
