package it.unicas.omnimove.dto;
import lombok.Data;
@Data
public class ChatRequest {
    private String message;
    private String language = "en";
}
