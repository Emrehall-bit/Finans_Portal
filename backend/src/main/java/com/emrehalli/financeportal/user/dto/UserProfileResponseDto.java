package com.emrehalli.financeportal.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileResponseDto {

    private UserResponseDto user;
    private boolean authenticated;
    private String authProvider;
}
