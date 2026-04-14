package it.unicas.cassitrack.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.unicas.cassitrack.dto.ChatRequest;
import it.unicas.cassitrack.dto.ChatResponse;
import it.unicas.cassitrack.service.AiOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoint for the AI chatbot assistant.
 *
 * POST /api/v1/ai/chat
 *
 * Receives a natural language question and returns
 * an AI-generated answer based on live bus data.
 */
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI Assistant",
        description = "Natural language bus information")
public class AiController {

    private final AiOrchestrationService aiService;

    @PostMapping("/chat")
    @Operation(
            summary = "Ask the AI assistant",
            description =
                    "Send a natural language question about " +
                            "bus positions, ETAs, or schedule status. " +
                            "The AI reads live data to answer accurately."
    )
    public ResponseEntity<ChatResponse> chat(
            @RequestBody ChatRequest request
    ) {
        log.info("AI chat request: '{}'",
                request.getMessage());

        if (request.getMessage() == null
                || request.getMessage().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ChatResponse.builder()
                            .success(false)
                            .error("Message cannot be empty")
                            .answer("Please ask me a question.")
                            .build());
        }

        ChatResponse response = aiService.answer(
                request.getMessage(),
                request.getLanguage() != null
                        ? request.getLanguage() : "en"
        );

        return ResponseEntity.ok(response);
    }
}