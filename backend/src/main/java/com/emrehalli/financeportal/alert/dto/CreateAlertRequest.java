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
public class CreateAlertRequest {

    @NotBlank(message = "instrumentCode cannot be blank")
    private String instrumentCode;

    @NotNull(message = "conditionType cannot be null")
    private ConditionType conditionType;

    @NotNull(message = "targetPrice cannot be null")
    @DecimalMin(value = "0.0001", message = "targetPrice must be greater than zero")
    private BigDecimal targetPrice;
}
