package com.emrehalli.financeportal.technicalanalysis.drawing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class DrawingRequest {

    @NotBlank(message = "Ã‡izim tipi boÅŸ olamaz")
    private String drawingType;

    @NotBlank(message = "Zaman dilimi boÅŸ olamaz")
    private String timeframe;

    @NotNull(message = "Nokta listesi boÅŸ olamaz")
    private List<Map<String, Object>> points;

    private Map<String, Object> style;
    private String label;
}

