package com.emrehalli.financeportal.technicalanalysis.drawing.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class DrawingDtos {

    private DrawingDtos() {
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Request {

        @NotBlank(message = "Cizim tipi bos olamaz")
        @Size(max = 50, message = "Cizim tipi en fazla 50 karakter olabilir")
        private String drawingType;

        @NotBlank(message = "Zaman dilimi bos olamaz")
        @Size(max = 20, message = "Zaman dilimi en fazla 20 karakter olabilir")
        private String timeframe;

        @NotNull(message = "Nokta listesi bos olamaz")
        @Size(min = 1, max = 100, message = "Nokta listesi 1 ile 100 nokta arasinda olmali")
        private List<Map<String, Object>> points;

        private Map<String, Object> style;

        @Size(max = 100, message = "Etiket en fazla 100 karakter olabilir")
        private String label;
    }

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Response {
        private Long id;
        private Long instrumentId;
        private String timeframe;
        private String drawingType;
        private List<Map<String, Object>> points;
        private Map<String, Object> style;
        private String label;
        private boolean isAlertLinked;
        private Long linkedAlertId;
        private boolean isPremiumFeature;
        private OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class LinkAlertRequest {

        @NotNull(message = "Alert ID boş olamaz")
        private Long alertId;
    }
}
