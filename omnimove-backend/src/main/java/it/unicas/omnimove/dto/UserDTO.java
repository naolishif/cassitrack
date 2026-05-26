package it.unicas.omnimove.dto;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UserDTO {
    private Long   id;
    private String name;
    private String email;
    private String role;
}
