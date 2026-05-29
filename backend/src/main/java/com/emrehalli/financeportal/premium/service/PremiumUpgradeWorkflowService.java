package com.emrehalli.financeportal.premium.service;

import com.emrehalli.financeportal.premium.entity.PremiumSubscription;
import jakarta.annotation.PreDestroy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kie.api.event.process.DefaultProcessEventListener;
import org.kie.api.event.process.ProcessNodeTriggeredEvent;
import org.kie.api.KieBase;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.runtime.process.WorkflowProcessInstance;
import org.kie.internal.io.ResourceFactory;
import org.kie.internal.utils.KieHelper;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class PremiumUpgradeWorkflowService {

    private static final Logger logger = LogManager.getLogger(PremiumUpgradeWorkflowService.class);
    private static final String PROCESS_ID = "premium-upgrade-process";
    private static final String PAYMENT_SIGNAL = "premium-payment-outcome";
    private static final String CANCEL_SIGNAL = "premium-cancel-requested";
    private static final String ACTIVE_CANCEL_SIGNAL = "premium-active-cancel-requested";

    private final PremiumSubscriptionService premiumSubscriptionService;
    private final KieSession kieSession;

    public PremiumUpgradeWorkflowService(@Lazy PremiumSubscriptionService premiumSubscriptionService) {
        this.premiumSubscriptionService = premiumSubscriptionService;
        this.kieSession = buildSession();
        registerProcessListener();
    }

    public Long startUpgradeProcess(Long subscriptionId) {
        synchronized (kieSession) {
            ProcessInstance instance = kieSession.startProcess(PROCESS_ID, Map.of(
                    "subscriptionId", subscriptionId,
                    "paymentSuccess", Boolean.FALSE
            ));
            logger.info("Premium workflow started. subscriptionId={}, processInstanceId={}", subscriptionId, instance.getId());
            return instance.getId();
        }
    }

    public void signalPaymentOutcome(PremiumSubscription subscription, boolean paymentSuccess) {
        synchronized (kieSession) {
            WorkflowProcessInstance instance = getActiveInstance(subscription);
            instance.setVariable("paymentSuccess", paymentSuccess);
            kieSession.signalEvent(PAYMENT_SIGNAL, subscription.getId(), subscription.getProcessInstanceId());
            logger.info("Premium workflow payment outcome signalled. subscriptionId={}, processInstanceId={}, paymentSuccess={}",
                    subscription.getId(), subscription.getProcessInstanceId(), paymentSuccess);
        }
    }

    public void signalCancel(PremiumSubscription subscription) {
        synchronized (kieSession) {
            getActiveInstance(subscription);
            kieSession.signalEvent(CANCEL_SIGNAL, subscription.getId(), subscription.getProcessInstanceId());
            logger.info("Premium workflow cancel signalled. subscriptionId={}, processInstanceId={}",
                    subscription.getId(), subscription.getProcessInstanceId());
        }
    }

    @PreDestroy
    public void close() {
        kieSession.dispose();
    }

    public void signalActiveCancellation(PremiumSubscription subscription) {
        synchronized (kieSession) {
            getActiveInstance(subscription);
            kieSession.signalEvent(ACTIVE_CANCEL_SIGNAL, subscription.getId(), subscription.getProcessInstanceId());
            logger.info("Premium workflow active cancellation signalled. subscriptionId={}, processInstanceId={}",
                    subscription.getId(), subscription.getProcessInstanceId());
        }
    }

    public boolean hasActiveInstance(Long processInstanceId) {
        if (processInstanceId == null) return false;
        synchronized (kieSession) {
            return kieSession.getProcessInstance(processInstanceId) instanceof WorkflowProcessInstance;
        }
    }

    private WorkflowProcessInstance getActiveInstance(PremiumSubscription subscription) {
        if (subscription.getProcessInstanceId() == null) {
            throw new IllegalStateException("Premium workflow instance id is missing");
        }
        ProcessInstance processInstance = kieSession.getProcessInstance(subscription.getProcessInstanceId());
        if (!(processInstance instanceof WorkflowProcessInstance workflowProcessInstance)) {
            throw new IllegalStateException("Premium workflow instance is not active for subscription " + subscription.getId());
        }
        return workflowProcessInstance;
    }

    private KieSession buildSession() {
        KieHelper helper = new KieHelper();
        helper.addResource(ResourceFactory.newClassPathResource("processes/premium-upgrade.bpmn2"), ResourceType.BPMN2);
        KieBase kieBase = helper.build();
        return kieBase.newKieSession();
    }

    private void registerProcessListener() {
        kieSession.addEventListener(new DefaultProcessEventListener() {
            @Override
            public void afterNodeTriggered(ProcessNodeTriggeredEvent event) {
                if (!(event.getProcessInstance() instanceof WorkflowProcessInstance workflowProcessInstance)) {
                    return;
                }
                Object variable = workflowProcessInstance.getVariable("subscriptionId");
                if (!(variable instanceof Number number)) {
                    return;
                }
                Long subscriptionId = number.longValue();
                String nodeName = event.getNodeInstance().getNodeName();
                switch (nodeName) {
                    case "PREMIUM_REQUESTED"      -> premiumSubscriptionService.markPremiumRequested(subscriptionId);
                    case "PAYMENT_PENDING"        -> premiumSubscriptionService.markPaymentPending(subscriptionId);
                    case "ACTIVE"                 -> premiumSubscriptionService.activatePremium(subscriptionId);
                    case "PAYMENT_FAILED"         -> premiumSubscriptionService.markPaymentFailed(subscriptionId);
                    case "CANCELLED"              -> premiumSubscriptionService.cancelUpgradeInternal(subscriptionId);
                    case "CANCELLATION_REQUESTED" -> premiumSubscriptionService.markCancellationRequested(subscriptionId);
                    case "CANCELLED_FROM_ACTIVE"  -> premiumSubscriptionService.cancelActivePremiumInternal(subscriptionId);
                    default -> {
                    }
                }
            }
        });
    }
}




