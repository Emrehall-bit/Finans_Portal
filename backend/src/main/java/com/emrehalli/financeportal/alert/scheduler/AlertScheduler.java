package com.emrehalli.financeportal.alert.scheduler;

import com.emrehalli.financeportal.alert.service.AlertService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AlertScheduler {

    private final AlertService alertService;

    public AlertScheduler(AlertService alertService) {
        this.alertService = alertService;
    }

    @Scheduled(fixedDelayString = "${alert.scheduler.fixed-delay-ms:60000}")
    public void evaluateAlerts() {
        alertService.evaluateActiveAlerts();
    }
}
