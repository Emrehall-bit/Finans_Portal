package com.emrehalli.financeportal.alert.dto;

import com.emrehalli.financeportal.alert.enums.ConditionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Schema(description = "Fiyat alarmı oluşturma isteği")
public class CreateAlertRequest {

    @NotBlank(message = "{validation.instrumentCode.blank}")
    @Schema(description = "Enstrüman kodu", example = "THYAO", requiredMode = Schema.RequiredMode.REQUIRED)
    private String instrumentCode;

    @NotNull(message = "{validation.conditionType.required}")
    @Schema(description = "Koşul tipi (ABOVE: üstüne çıkınca, BELOW: altına düşünce)", example = "ABOVE", requiredMode = Schema.RequiredMode.REQUIRED)
    private ConditionType conditionType;

    @NotNull(message = "{validation.targetPrice.required}")
    @DecimalMin(value = "0.0001", message = "{validation.targetPrice.positive}")
    @Schema(description = "Hedef fiyat", example = "220.00", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal targetPrice;
}

