package com.emrehalli.financeportal.ai.controller;

import com.emrehalli.financeportal.ai.context.AiContextEnricher;
import com.emrehalli.financeportal.ai.dto.AiChatRequest;
import com.emrehalli.financeportal.ai.dto.AiChatResponse;
import com.emrehalli.financeportal.ai.postprocess.AiResponsePostProcessor;
import com.emrehalli.financeportal.ai.prompt.ChatPromptBuilder;
import com.emrehalli.financeportal.ai.provider.AiResponse;
import com.emrehalli.financeportal.ai.provider.AiTaskType;
import com.emrehalli.financeportal.ai.service.AiGatewayService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/ai")
public class AiChatController {

    private static final Logger logger = LogManager.getLogger(AiChatController.class);
    private static final String UNAVAILABLE_REPLY =
            "Şu an yanıt üretilemiyor, lütfen daha sonra tekrar deneyin.";

    private final AiGatewayService aiGatewayService;
    private final ChatPromptBuilder chatPromptBuilder;
    private final AiContextEnricher contextEnricher;
    private final AiResponsePostProcessor postProcessor;
    private final ObjectMapper objectMapper;

    public AiChatController(AiGatewayService aiGatewayService,
                            ChatPromptBuilder chatPromptBuilder,
                            AiContextEnricher contextEnricher,
                            AiResponsePostProcessor postProcessor,
                            ObjectMapper objectMapper) {
        this.aiGatewayService = aiGatewayService;
        this.chatPromptBuilder = chatPromptBuilder;
        this.contextEnricher = contextEnricher;
        this.postProcessor = postProcessor;
        this.objectMapper = objectMapper;
    }

    /**
     * Chat endpoint. No cache — every request goes to the provider.
     * Routing: primary GEMINI, fallback GROQ (configured in AiProperties).
     */
    @PostMapping("/chat")
    public ResponseEntity<AiChatResponse> chat(@RequestBody AiChatRequest request) {
        if (request.message() == null || request.message().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new AiChatResponse("Mesaj boş olamaz.", null, false));
        }

        logger.info("AI chat request. messageLength={}, contextType={}",
                request.message().length(),
                request.context() != null ? request.context().type() : "none");

        String contextBlock = contextEnricher.buildContextBlock(request.context());
        String prompt = chatPromptBuilder.build(request.message(), contextBlock);

        Optional<AiResponse> response = aiGatewayService.generate(AiTaskType.CHAT, prompt);

        if (response.isEmpty()) {
            logger.warn("AI chat: no response from any provider.");
            return ResponseEntity.ok(new AiChatResponse(UNAVAILABLE_REPLY, null, false));
        }

        AiResponse aiResponse = response.get();
        String reply = postProcessor.process(extractReply(aiResponse.content()), AiTaskType.CHAT);

        logger.info("AI chat success. provider={}, fallback={}, replyLength={}",
                aiResponse.provider(), aiResponse.fallbackUsed(), reply.length());

        return ResponseEntity.ok(new AiChatResponse(
                reply,
                aiResponse.provider().name().toLowerCase(Locale.ROOT),
                aiResponse.fallbackUsed()
        ));
    }

    // ── Helpers ───────────────────────────────────────────────────

    /**
     * Extracts the "reply" field from the provider's JSON response.
     * Falls back to the raw content string if JSON parsing fails —
     * so the endpoint is robust even if the provider ignores the JSON instruction.
     */
    private String extractReply(String content) {
        if (content == null || content.isBlank()) {
            return UNAVAILABLE_REPLY;
        }
        try {
            String cleaned = content.trim()
                    .replaceAll("(?i)^\\s*```json\\s*", "")
                    .replaceAll("^\\s*```\\s*", "")
                    .replaceAll("\\s*```\\s*$", "")
                    .trim();
            int first = cleaned.indexOf('{');
            int last = cleaned.lastIndexOf('}');
            if (first >= 0 && last > first) {
                JsonNode root = objectMapper.readTree(cleaned.substring(first, last + 1));
                String reply = root.path("reply").asText(null);
                if (reply != null && !reply.isBlank()) {
                    return reply.trim();
                }
            }
        } catch (Exception e) {
            logger.debug("Chat reply JSON parse skipped, using raw content. reason={}", e.getMessage());
        }
        return content.trim();
    }

}
