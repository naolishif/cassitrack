package it.unicas.omnimove.dto;
import lombok.Data;
@Data
public class TravellerUpdateRequest {
    private String name;
    private String email;
    private String password;
}
