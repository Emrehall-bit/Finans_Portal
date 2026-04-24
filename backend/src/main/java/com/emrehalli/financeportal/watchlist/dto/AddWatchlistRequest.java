package com.emrehalli.financeportal.watchlist.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddWatchlistRequest {

    @NotBlank(message = "instrumentCode cannot be blank")
    private String instrumentCode;
}



