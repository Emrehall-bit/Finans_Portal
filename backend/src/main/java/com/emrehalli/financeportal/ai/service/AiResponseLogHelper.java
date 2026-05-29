package com.emrehalli.financeportal.ai.service;

import com.emrehalli.financeportal.ai.dto.AiResponseMetadata;
import com.emrehalli.financeportal.ai.provider.AiTaskType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class AiResponseLogHelper {

    private static final Logger logger = LogManager.getLogger(AiResponseLogHelper.class);

    public void log(AiTaskType taskType, AiResponseMetadata metadata) {
        if (metadata == null) {
            logger.info("AI response. taskType={}, providerUsed=null, modelUsed=null, fallbackUsed=false, aiEnhanced=false, deterministicFallbackUsed=true, cacheHit=false, dataQuality=UNKNOWN",
                    taskType);
            return;
        }

        logger.info(
                "AI response. taskType={}, providerUsed={}, modelUsed={}, fallbackUsed={}, aiEnhanced={}, deterministicFallbackUsed={}, cacheHit={}, dataQuality={}, generatedAt={}",
                taskType,
                metadata.providerUsed(),
                metadata.modelUsed(),
                metadata.fallbackUsed(),
                metadata.aiEnhanced(),
                metadata.deterministicFallbackUsed(),
                metadata.cacheHit(),
                metadata.dataQuality(),
                metadata.generatedAt()
        );
    }
}




