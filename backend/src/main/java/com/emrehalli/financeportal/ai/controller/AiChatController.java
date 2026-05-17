package com.emrehalli.financeportal.ai.controller;

import com.emrehalli.financeportal.ai.context.AiContextEnricher;
import com.emrehalli.financeportal.ai.dto.AiChatRequest;
import com.emrehalli.financeportal.ai.dto.AiChatResponse;
import com.emrehalli.financeportal.ai.dto.AiResponseMetadata;
import com.emrehalli.financeportal.ai.postprocess.AiResponsePostProcessor;
import com.emrehalli.financeportal.ai.prompt.ChatPromptBuilder;
import com.emrehalli.financeportal.ai.provider.AiResponse;
import com.emrehalli.financeportal.ai.provider.AiTaskType;
import com.emrehalli.financeportal.ai.service.AiGatewayService;
import com.emrehalli.financeportal.ai.service.AiResponseLogHelper;
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
            "Su an yanit uretilemiyor, lutfen daha sonra tekrar deneyin.";

    private final AiGatewayService aiGatewayService;
    private final ChatPromptBuilder chatPromptBuilder;
    private final AiContextEnricher contextEnricher;
    private final AiResponsePostProcessor postProcessor;
    private final ObjectMapper objectMapper;
    private final AiResponseLogHelper responseLogHelper;

    public AiChatController(AiGatewayService aiGatewayService,
                            ChatPromptBuilder chatPromptBuilder,
                            AiContextEnricher contextEnricher,
                            AiResponsePostProcessor postProcessor,
                            ObjectMapper objectMapper,
                            AiResponseLogHelper responseLogHelper) {
        this.aiGatewayService = aiGatewayService;
        this.chatPromptBuilder = chatPromptBuilder;
        this.contextEnricher = contextEnricher;
        this.postProcessor = postProcessor;
        this.objectMapper = objectMapper;
        this.responseLogHelper = responseLogHelper;
    }

    @PostMapping("/chat")
    public ResponseEntity<AiChatResponse> chat(@RequestBody AiChatRequest request) {
        if (request.message() == null || request.message().isBlank()) {
            AiChatResponse response = new AiChatResponse(
                    "Mesaj bos olamaz.",
                    null,
                    false,
                    AiResponseMetadata.deterministic("LOW")
            );
            responseLogHelper.log(AiTaskType.CHAT, response.metadata());
            return ResponseEntity.badRequest().body(response);
        }

        logger.info("AI chat request. messageLength={}, contextType={}",
                request.message().length(),
                request.context() != null ? request.context().type() : "none");

        String contextBlock = contextEnricher.buildContextBlock(request.context());
        String prompt = chatPromptBuilder.build(request.message(), contextBlock);

        Optional<AiResponse> response = aiGatewayService.generate(AiTaskType.CHAT, prompt);
        if (response.isEmpty()) {
            logger.warn("AI chat: no response from any provider.");
            AiChatResponse chatResponse = new AiChatResponse(
                    UNAVAILABLE_REPLY,
                    null,
                    false,
                    AiResponseMetadata.deterministic("LOW")
            );
            responseLogHelper.log(AiTaskType.CHAT, chatResponse.metadata());
            return ResponseEntity.ok(chatResponse);
        }

        AiResponse aiResponse = response.get();
        String reply = postProcessor.process(extractReply(aiResponse.content()), AiTaskType.CHAT);

        logger.info("AI chat success. provider={}, fallback={}, replyLength={}",
                aiResponse.provider(), aiResponse.fallbackUsed(), reply.length());

        AiChatResponse chatResponse = new AiChatResponse(
                reply,
                aiResponse.provider().name().toLowerCase(Locale.ROOT),
                aiResponse.fallbackUsed(),
                AiResponseMetadata.fromAiResponse(aiResponse, "FULL")
        );
        responseLogHelper.log(AiTaskType.CHAT, chatResponse.metadata());
        return ResponseEntity.ok(chatResponse);
    }

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
        } catch (Exception exception) {
            logger.debug("Chat reply JSON parse skipped, using raw content. reason={}", exception.getMessage());
        }
        return content.trim();
    }
}
