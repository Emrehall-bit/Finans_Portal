package com.emrehalli.financeportal.ai.features.chat;

import com.emrehalli.financeportal.ai.core.dto.AiResponseMetadata;

public record AiChatResponse(
        String reply,
        String provider,
        boolean fallbackUsed,
        AiResponseMetadata metadata
) {}




