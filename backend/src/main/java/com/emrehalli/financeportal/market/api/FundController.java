package com.emrehalli.financeportal.market.api;

import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.market.provider.fund.dto.FundNavDto;
import com.emrehalli.financeportal.market.service.FundService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * REST controller for fund market data.
 */
@RestController
@RequestMapping("/api/v1/funds")
@AllArgsConstructor
public class FundController {

    private final FundService fundService;

    @GetMapping
    public ApiResponse<?> getFunds(@RequestParam(required = false, name = "type") String type) {
        if (type != null && !type.isBlank()) {
            List<FundNavDto> data = fundService.getByType(type);
            return ApiResponse.<List<FundNavDto>>builder()
                    .success(true)
                    .message("OK")
                    .data(data)
                    .dataDate(resolveListDataDate(data))
                    .build();
        }

        List<FundNavDto> data = fundService.getAll();
        return ApiResponse.<List<FundNavDto>>builder()
                .success(true)
                .message("OK")
                .data(data)
                .dataDate(resolveListDataDate(data))
                .build();
    }

    @GetMapping("/{code}")
    public ApiResponse<FundNavDto> getByCode(@PathVariable String code) {
        FundNavDto data = fundService.getByCode(code);
        return ApiResponse.<FundNavDto>builder()
                .success(true)
                .message("OK")
                .data(data)
                .dataDate(data != null && data.getNavDate() != null ? data.getNavDate().atStartOfDay() : null)
                .build();
    }

    private LocalDateTime resolveListDataDate(List<FundNavDto> funds) {
        return funds.stream()
                .map(FundNavDto::getNavDate)
                .filter(Objects::nonNull)
                .map(java.time.LocalDate::atStartOfDay)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }
}




