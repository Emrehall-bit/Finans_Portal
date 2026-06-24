package com.emrehalli.financeportal.premium.service;

import com.emrehalli.financeportal.premium.entity.PremiumSubscription;
import jakarta.annotation.PreDestroy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jbpm.runtime.manager.impl.DefaultRegisterableItemsFactory;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.process.WorkItemHandler;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.manager.RuntimeEngine;
import org.kie.api.runtime.manager.RuntimeEnvironment;
import org.kie.api.runtime.manager.RuntimeEnvironmentBuilder;
import org.kie.api.runtime.manager.RuntimeManager;
import org.kie.api.runtime.manager.RuntimeManagerFactory;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.runtime.process.WorkflowProcessInstance;
import org.kie.internal.io.ResourceFactory;
import org.kie.internal.runtime.manager.context.EmptyContext;
import org.kie.internal.runtime.manager.context.ProcessInstanceIdContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class PremiumUpgradeWorkflowService {

    private static final Logger logger = LogManager.getLogger(PremiumUpgradeWorkflowService.class);

    private static final String PROCESS_ID          = "premium-upgrade-process";
    private static final String PAYMENT_SIGNAL      = "premium-payment-outcome";
    private static final String CANCEL_SIGNAL       = "premium-cancel-requested";
    private static final String ACTIVE_CANCEL_SIGNAL = "premium-active-cancel-requested";

    private final RuntimeManager runtimeManager;

    public PremiumUpgradeWorkflowService(@Lazy PremiumSubscriptionService premiumSubscriptionService) {
        this.runtimeManager = buildRuntimeManager(premiumSubscriptionService);
    }

    public Long startUpgradeProcess(Long subscriptionId) {
        RuntimeEngine engine = runtimeManager.getRuntimeEngine(EmptyContext.get());
        try {
            KieSession session = engine.getKieSession();
            ProcessInstance instance = session.startProcess(PROCESS_ID, Map.of(
                    "subscriptionId", subscriptionId,
                    "paymentSuccess", Boolean.FALSE
            ));
            logger.info("Premium workflow started. subscriptionId={}, processInstanceId={}",
                    subscriptionId, instance.getId());
            return instance.getId();
        } finally {
            runtimeManager.disposeRuntimeEngine(engine);
        }
    }

    public void signalPaymentOutcome(PremiumSubscription subscription, boolean paymentSuccess) {
        long pid = subscription.getProcessInstanceId();
        RuntimeEngine engine = runtimeManager.getRuntimeEngine(ProcessInstanceIdContext.get(pid));
        try {
            KieSession session = engine.getKieSession();
            WorkflowProcessInstance wpi = (WorkflowProcessInstance) session.getProcessInstance(pid);
            wpi.setVariable("paymentSuccess", paymentSuccess);
            session.signalEvent(PAYMENT_SIGNAL, subscription.getId(), pid);
            logger.info("Premium workflow payment outcome signalled. subscriptionId={}, processInstanceId={}, paymentSuccess={}",
                    subscription.getId(), pid, paymentSuccess);
        } finally {
            runtimeManager.disposeRuntimeEngine(engine);
        }
    }

    public void signalCancel(PremiumSubscription subscription) {
        long pid = subscription.getProcessInstanceId();
        RuntimeEngine engine = runtimeManager.getRuntimeEngine(ProcessInstanceIdContext.get(pid));
        try {
            engine.getKieSession().signalEvent(CANCEL_SIGNAL, subscription.getId(), pid);
            logger.info("Premium workflow cancel signalled. subscriptionId={}, processInstanceId={}",
                    subscription.getId(), pid);
        } finally {
            runtimeManager.disposeRuntimeEngine(engine);
        }
    }

    public void signalActiveCancellation(PremiumSubscription subscription) {
        long pid = subscription.getProcessInstanceId();
        RuntimeEngine engine = runtimeManager.getRuntimeEngine(ProcessInstanceIdContext.get(pid));
        try {
            engine.getKieSession().signalEvent(ACTIVE_CANCEL_SIGNAL, subscription.getId(), pid);
            logger.info("Premium workflow active cancellation signalled. subscriptionId={}, processInstanceId={}",
                    subscription.getId(), pid);
        } finally {
            runtimeManager.disposeRuntimeEngine(engine);
        }
    }

    public boolean hasActiveInstance(Long processInstanceId) {
        if (processInstanceId == null) {
            return false;
        }
        try {
            RuntimeEngine engine = runtimeManager.getRuntimeEngine(
                    ProcessInstanceIdContext.get(processInstanceId));
            try {
                ProcessInstance pi = engine.getKieSession().getProcessInstance(processInstanceId);
                return pi != null && pi.getState() == ProcessInstance.STATE_ACTIVE;
            } finally {
                runtimeManager.disposeRuntimeEngine(engine);
            }
        } catch (Exception ex) {
            logger.warn("Could not retrieve jBPM process instance. processInstanceId={}", processInstanceId, ex);
            return false;
        }
    }

    @PreDestroy
    public void close() {
        runtimeManager.close();
    }

    private RuntimeManager buildRuntimeManager(PremiumSubscriptionService subscriptionService) {
        DefaultRegisterableItemsFactory itemsFactory = new DefaultRegisterableItemsFactory() {
            @Override
            public Map<String, WorkItemHandler> getWorkItemHandlers(RuntimeEngine runtime) {
                Map<String, WorkItemHandler> handlers = super.getWorkItemHandlers(runtime);
                handlers.put("PremiumRequested",     new PremiumServiceTaskHandler(subscriptionService::markPremiumRequested));
                handlers.put("PaymentPending",        new PremiumServiceTaskHandler(subscriptionService::markPaymentPending));
                handlers.put("ActivatePremium",       new PremiumServiceTaskHandler(subscriptionService::activatePremium));
                handlers.put("PaymentFailed",         new PremiumServiceTaskHandler(subscriptionService::markPaymentFailed));
                handlers.put("CancelUpgrade",         new PremiumServiceTaskHandler(subscriptionService::cancelUpgradeInternal));
                handlers.put("CancellationRequested", new PremiumServiceTaskHandler(subscriptionService::markCancellationRequested));
                handlers.put("CancelActivePremium",   new PremiumServiceTaskHandler(subscriptionService::cancelActivePremiumInternal));
                return handlers;
            }
        };

        RuntimeEnvironment env = RuntimeEnvironmentBuilder.Factory.get()
                .newEmptyBuilder()
                .addAsset(
                        ResourceFactory.newClassPathResource("processes/premium-upgrade.bpmn2"),
                        ResourceType.BPMN2)
                .registerableItemsFactory(itemsFactory)
                .get();

        return RuntimeManagerFactory.Factory.get()
                .newPerProcessInstanceRuntimeManager(env, "premium-upgrade");
    }
}
