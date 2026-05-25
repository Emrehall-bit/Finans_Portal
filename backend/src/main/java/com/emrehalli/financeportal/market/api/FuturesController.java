package com.emrehalli.financeportal.market.api;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.market.provider.futures.dto.FuturesContractDto;
import com.emrehalli.financeportal.market.service.FuturesService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * REST controller for futures market data.
 */
@RestController
@RequestMapping("/api/v1/futures")
@AllArgsConstructor
public class FuturesController {

    private final FuturesService futuresService;

    @GetMapping
    public ApiResponse<FuturesPageResponse> getAll(@RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        Page<FuturesContractDto> result = futuresService.getAll(PageRequest.of(page, size));
        FuturesPageResponse data = FuturesPageResponse.builder()
                .content(result.getContent())
                .totalElements(result.getTotalElements())
                .page(result.getNumber())
                .size(result.getSize())
                .build();

        return ApiResponse.<FuturesPageResponse>builder()
                .success(true)
                .message("OK")
                .data(data)
                .dataDate(resolveDataDate(result.getContent()))
                .build();
    }

    @GetMapping("/{contract}")
    public ApiResponse<FuturesContractDto> getByContract(@PathVariable String contract) {
        FuturesContractDto data = futuresService.getByContract(contract);
        return ApiResponse.<FuturesContractDto>builder()
                .success(true)
                .message("OK")
                .data(data)
                .dataDate(data != null ? data.getDataTimestamp() : null)
                .build();
    }

    private LocalDateTime resolveDataDate(List<FuturesContractDto> contracts) {
        return contracts.stream()
                .map(FuturesContractDto::getDataTimestamp)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    /**
     * Futures page response payload.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FuturesPageResponse {
        private List<FuturesContractDto> content;
        private long totalElements;
        private int page;
        private int size;
    }
}



