package it.unicas.omnimove.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.unicas.omnimove.dto.ChatRequest;
import it.unicas.omnimove.dto.ChatResponse;
import it.unicas.omnimove.service.AiOrchestrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name="AI Assistant", description="Natural language bus information powered by Claude API")
public class AiController {
    private final AiOrchestrationService aiService;

    @PostMapping("/chat")
    @Operation(summary="Ask the AI assistant",
        description="Answers in English or Italian using live CASSITRACK data.")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        if (request.getMessage() == null || request.getMessage().isBlank())
            return ResponseEntity.badRequest()
                .body(ChatResponse.builder().success(false).answer("Please ask a question.").build());
        return ResponseEntity.ok(
            aiService.answer(request.getMessage(),
                request.getLanguage() != null ? request.getLanguage() : "en"));
    }
}
