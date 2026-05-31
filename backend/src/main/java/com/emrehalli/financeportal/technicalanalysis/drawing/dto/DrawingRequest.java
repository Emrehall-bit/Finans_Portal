package com.emrehalli.financeportal.technicalanalysis.drawing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class DrawingRequest {

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
