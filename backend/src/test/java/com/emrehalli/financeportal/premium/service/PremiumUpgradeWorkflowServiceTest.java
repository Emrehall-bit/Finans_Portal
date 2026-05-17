package com.emrehalli.financeportal.premium.service;

import com.emrehalli.financeportal.premium.entity.PremiumSubscription;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PremiumUpgradeWorkflowServiceTest {

    private PremiumUpgradeWorkflowService workflowService;

    @AfterEach
    void tearDown() {
        if (workflowService != null) {
            workflowService.close();
        }
    }

    @Test
    void startProcess_runsRequestedAndPaymentPendingSteps() {
        PremiumSubscriptionService premiumSubscriptionService = mock(PremiumSubscriptionService.class);
        workflowService = new PremiumUpgradeWorkflowService(premiumSubscriptionService);

        Long processInstanceId = workflowService.startUpgradeProcess(11L);

        assertThat(processInstanceId).isNotNull();
        verify(premiumSubscriptionService).markPremiumRequested(11L);
        verify(premiumSubscriptionService).markPaymentPending(11L);
    }

    @Test
    void paymentSuccess_activatesPremium() {
        PremiumSubscriptionService premiumSubscriptionService = mock(PremiumSubscriptionService.class);
        workflowService = new PremiumUpgradeWorkflowService(premiumSubscriptionService);
        Long processInstanceId = workflowService.startUpgradeProcess(12L);

        workflowService.signalPaymentOutcome(subscription(12L, processInstanceId), true);

        verify(premiumSubscriptionService).activatePremium(12L);
    }

    @Test
    void paymentFail_marksFailed() {
        PremiumSubscriptionService premiumSubscriptionService = mock(PremiumSubscriptionService.class);
        workflowService = new PremiumUpgradeWorkflowService(premiumSubscriptionService);
        Long processInstanceId = workflowService.startUpgradeProcess(13L);

        workflowService.signalPaymentOutcome(subscription(13L, processInstanceId), false);

        verify(premiumSubscriptionService).markPaymentFailed(13L);
    }

    @Test
    void cancelSignal_marksCancelled() {
        PremiumSubscriptionService premiumSubscriptionService = mock(PremiumSubscriptionService.class);
        workflowService = new PremiumUpgradeWorkflowService(premiumSubscriptionService);
        Long processInstanceId = workflowService.startUpgradeProcess(14L);

        workflowService.signalCancel(subscription(14L, processInstanceId));

        verify(premiumSubscriptionService).cancelUpgradeInternal(14L);
    }

    private PremiumSubscription subscription(Long id, Long processInstanceId) {
        PremiumSubscription subscription = new PremiumSubscription();
        subscription.setId(id);
        subscription.setProcessInstanceId(processInstanceId);
        return subscription;
    }
}
