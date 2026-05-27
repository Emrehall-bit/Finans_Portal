package com.emrehalli.financeportal.analysis.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class LinkAlertRequest {

    @NotNull(message = "Alert ID boş olamaz")
    private Long alertId;
}
