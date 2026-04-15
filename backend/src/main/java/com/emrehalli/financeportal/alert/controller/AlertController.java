package com.emrehalli.financeportal.alert.controller;

import com.emrehalli.financeportal.alert.dto.AlertResponseDto;
import com.emrehalli.financeportal.alert.dto.CreateAlertRequest;
import com.emrehalli.financeportal.alert.service.AlertService;
import com.emrehalli.financeportal.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @PostMapping("/{userId}")
    public ApiResponse<AlertResponseDto> createAlert(@PathVariable Long userId,
                                                     @Valid @RequestBody CreateAlertRequest request) {
        AlertResponseDto response = alertService.createAlert(userId, request);

        return ApiResponse.<AlertResponseDto>builder()
                .success(true)
                .data(response)
                .message("Alert created successfully")
                .build();
    }

    @GetMapping("/user/{userId}")
    public ApiResponse<List<AlertResponseDto>> getUserAlerts(@PathVariable Long userId) {
        List<AlertResponseDto> response = alertService.getUserAlerts(userId);

        return ApiResponse.<List<AlertResponseDto>>builder()
                .success(true)
                .data(response)
                .message("User alerts fetched successfully")
                .build();
    }

    @PatchMapping("/{userId}/{alertId}/cancel")
    public ApiResponse<Void> cancelAlert(@PathVariable Long userId,
                                         @PathVariable Long alertId) {
        alertService.cancelAlert(userId, alertId);

        return ApiResponse.<Void>builder()
                .success(true)
                .data(null)
                .message("Alert cancelled successfully")
                .build();
    }
}
