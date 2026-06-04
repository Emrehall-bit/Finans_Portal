package com.emrehalli.financeportal.ai.dto;

import com.emrehalli.financeportal.ai.context.AiContext;

public record AiChatRequest(String message, AiContext context, String language) {}
