package com.emrehalli.financeportal.ai.context;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Structured context sent from the frontend to describe the user's current screen.
 * All fields are optional — a null AiContext degrades gracefully to a context-free chat.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiContext(
        AiContextType type,
        String symbol,
        String instrumentType,
        String screenName
) {}
