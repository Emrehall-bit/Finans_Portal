package com.emrehalli.financeportal.technicalanalysis.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.Map;

public final class IndicatorConfigDtos {

    private IndicatorConfigDtos() {
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Request {

        @NotBlank(message = "Gosterge tipi bos olamaz")
        @Size(max = 20, message = "Gosterge tipi en fazla 20 karakter olabilir")
        @Pattern(regexp = "SMA7|SMA20|SMA50|RSI14", flags = Pattern.Flag.CASE_INSENSITIVE,
                message = "Desteklenmeyen gosterge tipi")
        private String indicatorType;

        private Map<String, Object> parameters;
        private boolean isActive = true;
    }

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Response {
        private Long id;
        private String indicatorType;
        private Map<String, Object> parameters;
        private boolean isActive;
        private OffsetDateTime createdAt;
    }
}
