package com.emrehalli.financeportal.technicalanalysis.drawing.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class LinkAlertRequest {

    @NotNull(message = "Alert ID boÅŸ olamaz")
    private Long alertId;
}

