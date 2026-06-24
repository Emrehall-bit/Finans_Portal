package com.emrehalli.financeportal.premium.service;

import org.kie.api.runtime.process.WorkItem;
import org.kie.api.runtime.process.WorkItemHandler;
import org.kie.api.runtime.process.WorkItemManager;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Generic jBPM WorkItemHandler for premium workflow service tasks.
 * Each instance wraps one service method call keyed by task name.
 */
public class PremiumServiceTaskHandler implements WorkItemHandler {

    private final Consumer<Long> action;

    public PremiumServiceTaskHandler(Consumer<Long> action) {
        this.action = action;
    }

    @Override
    public void executeWorkItem(WorkItem workItem, WorkItemManager manager) {
        Long subscriptionId = (Long) workItem.getParameter("subscriptionId");
        action.accept(subscriptionId);
        manager.completeWorkItem(workItem.getId(), Map.of());
    }

    @Override
    public void abortWorkItem(WorkItem workItem, WorkItemManager manager) {
        manager.abortWorkItem(workItem.getId());
    }
}
