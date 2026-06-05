package com.emrehalli.financeportal.alert.scheduler;

import com.emrehalli.financeportal.common.logging.SchedulerLogSupport;
import com.emrehalli.financeportal.alert.service.AlertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AlertScheduler {

    private static final Logger logger = LoggerFactory.getLogger(AlertScheduler.class);

    private final AlertService alertService;

    public AlertScheduler(AlertService alertService) {
        this.alertService = alertService;
    }

    @Scheduled(fixedDelayString = "${alert.scheduler.fixed-delay-ms:${market.scheduler.crypto-rate-ms:600000}}")
    public void evaluateAlerts() {
        SchedulerLogSupport.Run run = SchedulerLogSupport.start("AlertScheduler.evaluateAlerts");
        try {
            alertService.evaluateActiveAlerts();
            run.log(logger, 1, 1, 0);
        } catch (Exception exception) {
            run.log(logger, 1, 0, 1, exception);
            throw exception;
        }
    }
}







