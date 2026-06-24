package com.emrehalli.financeportal.market.api;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.market.provider.bond.dto.BondRateDto;
import com.emrehalli.financeportal.market.service.BondService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/bonds")
@AllArgsConstructor
@Tag(name = "Tahviller", description = "Tahvil faiz oranı verileri")
public class BondController {

    private final BondService bondService;

    @Operation(summary = "Tahvil faiz oranlarını listele")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Tahvil faiz oranları başarıyla listelendi"))
    @GetMapping
    public ApiResponse<BondPageResponse> getAll(@RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        Page<BondRateDto> result = bondService.getAll(PageRequest.of(page, size));
        BondPageResponse data = BondPageResponse.builder()
                .content(result.getContent())
                .totalElements(result.getTotalElements())
                .page(result.getNumber())
                .size(result.getSize())
                .build();

        return ApiResponse.<BondPageResponse>builder()
                .success(true)
                .message("OK")
                .data(data)
                .dataDate(resolveDataDate(result.getContent()))
                .build();
    }

    @Operation(summary = "Tahvil detayını getir")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Tahvil detayı başarıyla döndürüldü"))
    @GetMapping("/{code}")
    public ApiResponse<BondRateDto> getByCode(@PathVariable String code) {
        BondRateDto data = bondService.getByCode(code);
        return ApiResponse.<BondRateDto>builder()
                .success(true)
                .message("OK")
                .data(data)
                .dataDate(data != null ? data.getDataTimestamp() : null)
                .build();
    }

    private LocalDateTime resolveDataDate(List<BondRateDto> rates) {
        return rates.stream()
                .map(BondRateDto::getDataTimestamp)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BondPageResponse {
        private List<BondRateDto> content;
        private long totalElements;
        private int page;
        private int size;
    }
}

