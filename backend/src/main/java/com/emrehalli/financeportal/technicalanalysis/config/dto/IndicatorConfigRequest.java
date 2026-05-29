package com.emrehalli.financeportal.technicalanalysis.config.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class IndicatorConfigRequest {

    @NotBlank(message = "GÃ¶sterge tipi boÅŸ olamaz")
    private String indicatorType;

    private Map<String, Object> parameters;
    private boolean isActive = true;
}

