package it.unicas.omnimove.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.unicas.omnimove.dto.ChatRequest;
import it.unicas.omnimove.dto.ChatResponse;
import it.unicas.omnimove.model.User;
import it.unicas.omnimove.repository.UserRepository;
import it.unicas.omnimove.service.AiOrchestrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI Assistant", description = "Natural language bus information powered by Claude API")
public class AiController {

    private final AiOrchestrationService aiService;
    private final UserRepository userRepository;

    @PostMapping("/chat")
    @Operation(summary = "Ask the AI assistant",
            description = "Answers in English or Italian using live CASSITRACK data, "
                    + "the conversation history, and (if logged in) the traveller's journey history.")
    public ResponseEntity<ChatResponse> chat(
            @RequestBody ChatRequest request,
            @AuthenticationPrincipal UserDetails principal) {

        if (request.getMessage() == null || request.getMessage().isBlank())
            return ResponseEntity.badRequest()
                    .body(ChatResponse.builder().success(false).answer("Please ask a question.").build());

        // Resolve the logged-in traveller's id for personalisation (null if anonymous)
        Long userId = null;
        if (principal != null) {
            User user = userRepository.findByEmail(principal.getUsername()).orElse(null);
            if (user != null) userId = user.getId();
        }

        return ResponseEntity.ok(aiService.answer(request, userId));
    }
}
