package it.unicas.cassitrack.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * What the frontend sends to the chat endpoint.
 *
 * Example:
 * {
 *   "message": "Is there a bus going to Campus soon?",
 *   "language": "en"
 * }
 */
@Data
public class ChatRequest {

    /** The user's question in natural language */
    private String message;

    /**
     * Language preference: "en" or "it"
     * The AI will respond in this language.
     * Defaults to "en" if not provided.
     */
    private String language = "en";
}