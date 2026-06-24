package com.emrehalli.financeportal.admin.controller;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.market.service.mock.MockDerivativesSeedService;
import com.emrehalli.financeportal.market.service.mock.MockSeedResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin endpoint for seeding mock VİOP and bond/interest-rate instruments.
 *
 * <p>Secured via {@code /api/v1/admin/**} path rule in SecurityConfig (requires ADMIN role).
 * The operation is idempotent: calling it multiple times does not create duplicate records.
 *
 * <pre>
 *   POST /api/v1/admin/markets/mock-derivatives/seed
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/admin/markets/mock-derivatives")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin - Mock Turev Yonetimi", description = "Mock VIOP ve tahvil/faiz enstrumanlari yonetimi")
public class MockDerivativesAdminController {

    private final MockDerivativesSeedService seedService;

    @Operation(summary = "Mock turev verilerini olustur", description = "Mock VIOP ve tahvil/faiz enstrumanlari ile fiyat verilerini olusturur (idempotent)")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Mock veriler basariyla olusturuldu"))
    @PostMapping("/seed")
    public ApiResponse<MockSeedResult> seed() {
        log.info("[MockDerivatives] Seed requested via admin endpoint");
        MockSeedResult result = seedService.seed();
        return ApiResponse.<MockSeedResult>builder()
                .success(true)
                .message(String.format(
                        "Mock seed tamamlandı: %d vadeli, %d tahvil/faiz enstrümanı; %d fiyat, %d geçmiş kaydı.",
                        result.futuresInstruments(),
                        result.bondInstruments(),
                        result.priceRecords(),
                        result.historyRecords()))
                .data(result)
                .build();
    }
}
