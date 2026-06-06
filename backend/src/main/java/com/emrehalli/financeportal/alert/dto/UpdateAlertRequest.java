package com.emrehalli.financeportal.alert.dto;

import com.emrehalli.financeportal.alert.enums.ConditionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class UpdateAlertRequest {

    @NotBlank(message = "{validation.instrumentCode.blank}")
    private String instrumentCode;

    @NotNull(message = "{validation.conditionType.required}")
    private ConditionType conditionType;

    @NotNull(message = "{validation.targetPrice.required}")
    @DecimalMin(value = "0.0001", message = "{validation.targetPrice.positive}")
    private BigDecimal targetPrice;
}
