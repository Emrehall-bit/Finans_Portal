package com.emrehalli.financeportal.technicalanalysis.config.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class IndicatorConfigRequest {

    @NotBlank(message = "Gosterge tipi bos olamaz")
    @Size(max = 20, message = "Gosterge tipi en fazla 20 karakter olabilir")
    @Pattern(regexp = "SMA7|SMA20|SMA50|RSI14", flags = Pattern.Flag.CASE_INSENSITIVE,
            message = "Desteklenmeyen gosterge tipi")
    private String indicatorType;

    private Map<String, Object> parameters;
    private boolean isActive = true;
}
