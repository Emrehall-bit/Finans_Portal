package com.emrehalli.financeportal.ai.features.chat;

import com.emrehalli.financeportal.ai.core.context.AiContext;

public record AiChatRequest(String message, AiContext context, String language) {}
