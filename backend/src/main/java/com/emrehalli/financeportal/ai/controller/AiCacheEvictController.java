package com.emrehalli.financeportal.ai.controller;

import com.emrehalli.financeportal.ai.service.AiResponseCacheService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/ai/cache")
public class AiCacheEvictController {

    private final AiResponseCacheService aiResponseCacheService;

    public AiCacheEvictController(AiResponseCacheService aiResponseCacheService) {
        this.aiResponseCacheService = aiResponseCacheService;
    }

    // DELETE /api/v1/admin/ai/cache/fundamental/THYAO
    // DELETE /api/v1/admin/ai/cache/technical/THYAO
    @DeleteMapping("/{type}/{symbol}")
    public ResponseEntity<Map<String, String>> evict(@PathVariable String type, @PathVariable String symbol) {
        String key = "ai:" + type.toLowerCase(Locale.ROOT) + ":" + symbol.toUpperCase(Locale.ROOT);
        aiResponseCacheService.evict(key);
        return ResponseEntity.ok(Map.of("evicted", key));
    }

    // DELETE /api/v1/admin/ai/cache  →  tüm AI cache'i temizler
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> evictAll() {
        int count = aiResponseCacheService.evictAll();
        return ResponseEntity.ok(Map.of("evictedCount", count));
    }
}



