package it.unicas.omnimove.dto;

import lombok.Data;
import java.util.List;

@Data
public class ChatRequest {
    private String message;
    private String language = "en";

    /**
     * Previous messages in this conversation (oldest first).
     * Each item is {role: "user"|"assistant", content: "..."}.
     * The frontend sends the running history so the AI has memory
     * across turns. Optional — if null, treated as a fresh conversation.
     */
    private List<ChatTurn> history;

    @Data
    public static class ChatTurn {
        private String role;     // "user" or "assistant"
        private String content;
    }
}
