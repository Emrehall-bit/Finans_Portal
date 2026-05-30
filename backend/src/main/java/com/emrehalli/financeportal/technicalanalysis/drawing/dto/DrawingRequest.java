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

    @NotBlank(message = "Çizim tipi boş olamaz")
    private String drawingType;

    @NotBlank(message = "Zaman dilimi boş olamaz")
    private String timeframe;

    @NotNull(message = "Nokta listesi boş olamaz")
    private List<Map<String, Object>> points;

    private Map<String, Object> style;
    private String label;
}

