package it.unicas.omnimove.dto;
import lombok.Builder;
import lombok.Data;
@Data @Builder
public class ChatResponse {
    private String answer;
    private boolean success;
    private String error;
}
