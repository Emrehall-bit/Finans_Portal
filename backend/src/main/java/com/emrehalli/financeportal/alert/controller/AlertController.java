package com.emrehalli.financeportal.alert.controller;

import com.emrehalli.financeportal.alert.dto.AlertResponseDto;
import com.emrehalli.financeportal.alert.dto.CreateAlertRequest;
import com.emrehalli.financeportal.alert.dto.UpdateAlertRequest;
import com.emrehalli.financeportal.alert.service.AlertService;
import com.emrehalli.financeportal.common.i18n.AppMessageSource;
import com.emrehalli.financeportal.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/alerts")
@Tag(name = "Fiyat Alarmları", description = "Kullanıcı tanımlı fiyat alarmı CRUD işlemleri")
public class AlertController {

    private final AlertService alertService;
    private final AppMessageSource appMessageSource;

    public AlertController(AlertService alertService, AppMessageSource appMessageSource) {
        this.alertService = alertService;
        this.appMessageSource = appMessageSource;
    }

    @Operation(summary = "Yeni fiyat alarmı oluştur", description = "Belirtilen kullanıcı için yeni bir aktif fiyat alarmı oluşturur")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Alarm başarıyla oluşturuldu"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Geçersiz alarm yapılandırması"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Kullanıcı bulunamadı")
    })
    @PostMapping("/{userId}")
    public ApiResponse<AlertResponseDto> createAlert(@PathVariable Long userId,
                                                     @Valid @RequestBody CreateAlertRequest request) {
        AlertResponseDto response = alertService.createAlert(userId, request);

        return ApiResponse.<AlertResponseDto>builder()
                .success(true)
                .data(response)
                .message(appMessageSource.get("alert.created"))
                .build();
    }

    @Operation(summary = "Kullanıcının alarmlarını listele", description = "Belirtilen kullanıcıya ait tüm alarmları en yeniden en eskiye doğru listeler")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Alarm listesi başarıyla getirildi"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Kullanıcı bulunamadı")
    })
    @GetMapping("/user/{userId}")
    public ApiResponse<List<AlertResponseDto>> getUserAlerts(@PathVariable Long userId) {
        List<AlertResponseDto> response = alertService.getUserAlerts(userId);

        return ApiResponse.<List<AlertResponseDto>>builder()
                .success(true)
                .data(response)
                .message(appMessageSource.get("alert.list.fetched"))
                .build();
    }

    @Operation(summary = "Fiyat alarmını güncelle", description = "Belirtilen kullanıcıya ait mevcut bir aktif alarmı günceller")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Alarm başarıyla güncellendi"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Geçersiz alarm yapılandırması"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Alarm veya kullanıcı bulunamadı")
    })
    @PutMapping("/{userId}/{alertId}")
    public ApiResponse<AlertResponseDto> updateAlert(@PathVariable Long userId,
                                                     @PathVariable Long alertId,
                                                     @Valid @RequestBody UpdateAlertRequest request) {
        AlertResponseDto response = alertService.updateAlert(userId, alertId, request);

        return ApiResponse.<AlertResponseDto>builder()
                .success(true)
                .data(response)
                .message(appMessageSource.get("alert.updated"))
                .build();
    }

    @Operation(summary = "Fiyat alarmını sil", description = "Belirtilen kullanıcıya ait bir alarmı kalıcı olarak siler")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Alarm başarıyla silindi"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Alarm veya kullanıcı bulunamadı")
    })
    @DeleteMapping("/{userId}/{alertId}")
    public ApiResponse<Void> deleteAlert(@PathVariable Long userId,
                                         @PathVariable Long alertId) {
        alertService.deleteAlert(userId, alertId);

        return ApiResponse.<Void>builder()
                .success(true)
                .data(null)
                .message(appMessageSource.get("alert.deleted"))
                .build();
    }
}

