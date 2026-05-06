package com.emrehalli.financeportal.watchlist.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddWatchlistRequest {

    @NotBlank(message = "{validation.instrumentCode.blank}")
    private String instrumentCode;
}



