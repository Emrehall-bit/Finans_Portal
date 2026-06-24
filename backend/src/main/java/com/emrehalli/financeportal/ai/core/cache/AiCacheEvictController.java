package com.emrehalli.financeportal.ai.core.cache;

import com.emrehalli.financeportal.ai.core.cache.AiResponseCacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Map;

@Tag(name = "Admin - AI Cache", description = "AI önbellek yönetimi")
@RestController
@RequestMapping("/api/v1/admin/ai/cache")
public class AiCacheEvictController {

    private final AiResponseCacheService aiResponseCacheService;

    public AiCacheEvictController(AiResponseCacheService aiResponseCacheService) {
        this.aiResponseCacheService = aiResponseCacheService;
    }

    @Operation(summary = "Enstrüman AI önbelleğini temizle")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "AI önbelleği başarıyla temizlendi"))
    @DeleteMapping("/{type}/{symbol}")
    public ResponseEntity<Map<String, String>> evict(@PathVariable String type, @PathVariable String symbol) {
        String key = "ai:" + type.toLowerCase(Locale.ROOT) + ":" + symbol.toUpperCase(Locale.ROOT);
        aiResponseCacheService.evict(key);
        return ResponseEntity.ok(Map.of("evicted", key));
    }

    @Operation(summary = "Tüm AI önbelleğini temizle")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Tüm AI önbelleği başarıyla temizlendi"))
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> evictAll() {
        int count = aiResponseCacheService.evictAll();
        return ResponseEntity.ok(Map.of("evictedCount", count));
    }
}

