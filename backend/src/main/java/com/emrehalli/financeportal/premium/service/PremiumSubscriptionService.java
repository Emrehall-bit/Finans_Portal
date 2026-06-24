package com.emrehalli.financeportal.premium.service;

import com.emrehalli.financeportal.common.exception.BadRequestException;
import com.emrehalli.financeportal.common.exception.ResourceNotFoundException;
import com.emrehalli.financeportal.premium.dto.PremiumSubscriptionResponse;
import com.emrehalli.financeportal.premium.entity.PremiumSubscription;
import com.emrehalli.financeportal.premium.entity.PremiumSubscriptionStatus;
import com.emrehalli.financeportal.premium.repository.PremiumSubscriptionRepository;
import com.emrehalli.financeportal.user.entity.User;
import com.emrehalli.financeportal.user.entity.UserRole;
import com.emrehalli.financeportal.user.service.UserService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PremiumSubscriptionService {

    private static final Logger logger = LogManager.getLogger(PremiumSubscriptionService.class);

    private final PremiumSubscriptionRepository premiumSubscriptionRepository;
    private final UserService userService;
    private final PremiumUpgradeWorkflowService workflowService;

    public PremiumSubscriptionService(PremiumSubscriptionRepository premiumSubscriptionRepository,
                                      UserService userService,
                                      PremiumUpgradeWorkflowService workflowService) {
        this.premiumSubscriptionRepository = premiumSubscriptionRepository;
        this.userService = userService;
        this.workflowService = workflowService;
    }

    @Transactional
    public PremiumSubscriptionResponse requestUpgrade() {
        User user = userService.getCurrentAuthenticatedUserEntity();
        PremiumSubscription latest = premiumSubscriptionRepository.findTopByUserIdOrderByCreatedAtDesc(user.getId()).orElse(null);

        if (latest != null && latest.getStatus().isPending()) {
            return toResponse(latest);
        }
        if (user.getRole() == UserRole.USER_PREMIUM || user.getRole() == UserRole.ADMIN) {
            return latest != null
                    ? toResponse(latest)
                    : PremiumSubscriptionResponse.currentRoleOnly(user.getRole());
        }

        PremiumSubscription subscription = premiumSubscriptionRepository.save(PremiumSubscription.builder()
                .user(user)
                .status(PremiumSubscriptionStatus.PREMIUM_REQUESTED)
                .requestedAt(LocalDateTime.now())
                .build());

        Long processInstanceId = workflowService.startUpgradeProcess(subscription.getId());
        subscription.setProcessInstanceId(processInstanceId);
        PremiumSubscription saved = premiumSubscriptionRepository.save(subscription);
        logger.info("Premium upgrade requested. subscriptionId={}, userId={}, processInstanceId={}",
                saved.getId(), user.getId(), processInstanceId);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PremiumSubscriptionResponse getCurrentStatus() {
        User user = userService.getCurrentAuthenticatedUserEntity();
        return premiumSubscriptionRepository.findTopByUserIdOrderByCreatedAtDesc(user.getId())
                .map(this::toResponse)
                .orElseGet(() -> PremiumSubscriptionResponse.currentRoleOnly(user.getRole()));
    }

    @Transactional
    public PremiumSubscriptionResponse cancelCurrentUpgrade() {
        User user = userService.getCurrentAuthenticatedUserEntity();
        PremiumSubscription subscription = premiumSubscriptionRepository.findTopByUserIdOrderByCreatedAtDesc(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Premium subscription not found"));

        if (subscription.getStatus().isPending()) {
            workflowService.signalCancel(subscription);
        } else if (subscription.getStatus() == PremiumSubscriptionStatus.ACTIVE) {
            workflowService.signalActiveCancellation(subscription);
        } else {
            throw new BadRequestException("Premium subscription cannot be cancelled in its current state: " + subscription.getStatus());
        }

        PremiumSubscription updated = premiumSubscriptionRepository.findById(subscription.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Premium subscription not found"));
        return toResponse(updated);
    }

    @Transactional
    public PremiumSubscriptionResponse markPaymentSuccess(Long subscriptionId) {
        PremiumSubscription subscription = getSubscriptionEntity(subscriptionId);
        if (!subscription.getStatus().isPending()) {
            throw new BadRequestException("Payment success can only be applied to pending subscriptions");
        }

        workflowService.signalPaymentOutcome(subscription, true);

        PremiumSubscription updated = getSubscriptionEntity(subscriptionId);
        return toResponse(updated);
    }

    @Transactional
    public PremiumSubscriptionResponse markPaymentFail(Long subscriptionId) {
        PremiumSubscription subscription = getSubscriptionEntity(subscriptionId);
        if (!subscription.getStatus().isPending()) {
            throw new BadRequestException("Payment fail can only be applied to pending subscriptions");
        }

        workflowService.signalPaymentOutcome(subscription, false);

        PremiumSubscription updated = getSubscriptionEntity(subscriptionId);
        return toResponse(updated);
    }

    @Transactional(readOnly = true)
    public List<PremiumSubscriptionResponse> getPendingSubscriptions() {
        return premiumSubscriptionRepository.findAllByStatusInOrderByCreatedAtDesc(
                        List.of(PremiumSubscriptionStatus.PREMIUM_REQUESTED, PremiumSubscriptionStatus.PAYMENT_PENDING))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void markPremiumRequested(Long subscriptionId) {
        PremiumSubscription subscription = getSubscriptionEntity(subscriptionId);
        subscription.setStatus(PremiumSubscriptionStatus.PREMIUM_REQUESTED);
        premiumSubscriptionRepository.save(subscription);
        logger.info("Premium workflow state entered. subscriptionId={}, status=PREMIUM_REQUESTED", subscriptionId);
    }

    @Transactional
    public void markPaymentPending(Long subscriptionId) {
        PremiumSubscription subscription = getSubscriptionEntity(subscriptionId);
        subscription.setStatus(PremiumSubscriptionStatus.PAYMENT_PENDING);
        premiumSubscriptionRepository.save(subscription);
        logger.info("Premium workflow state entered. subscriptionId={}, status=PAYMENT_PENDING", subscriptionId);
    }

    @Transactional
    public void activatePremium(Long subscriptionId) {
        PremiumSubscription subscription = getSubscriptionEntity(subscriptionId);
        subscription.setStatus(PremiumSubscriptionStatus.ACTIVE);
        subscription.setActivatedAt(LocalDateTime.now());
        premiumSubscriptionRepository.save(subscription);
        userService.updateUserRoleForSystem(subscription.getUser(), UserRole.USER_PREMIUM, "premium-upgrade-activated");
        logger.info("Premium workflow completed. subscriptionId={}, userId={}, status=ACTIVE",
                subscriptionId, subscription.getUser().getId());
    }

    @Transactional
    public void markPaymentFailed(Long subscriptionId) {
        PremiumSubscription subscription = getSubscriptionEntity(subscriptionId);
        subscription.setStatus(PremiumSubscriptionStatus.PAYMENT_FAILED);
        premiumSubscriptionRepository.save(subscription);
        logger.info("Premium workflow completed. subscriptionId={}, status=PAYMENT_FAILED", subscriptionId);
    }

    @Transactional
    public void cancelUpgradeInternal(Long subscriptionId) {
        PremiumSubscription subscription = getSubscriptionEntity(subscriptionId);
        subscription.setStatus(PremiumSubscriptionStatus.CANCELLED);
        subscription.setCancelledAt(LocalDateTime.now());
        premiumSubscriptionRepository.save(subscription);
        logger.info("Premium workflow completed. subscriptionId={}, status=CANCELLED", subscriptionId);
    }

    @Transactional
    public void markCancellationRequested(Long subscriptionId) {
        PremiumSubscription subscription = getSubscriptionEntity(subscriptionId);
        subscription.setStatus(PremiumSubscriptionStatus.CANCELLATION_REQUESTED);
        premiumSubscriptionRepository.save(subscription);
        logger.info("Premium workflow state entered. subscriptionId={}, status=CANCELLATION_REQUESTED", subscriptionId);
    }

    @Transactional
    public void cancelActivePremiumInternal(Long subscriptionId) {
        PremiumSubscription subscription = getSubscriptionEntity(subscriptionId);
        subscription.setStatus(PremiumSubscriptionStatus.CANCELLED);
        subscription.setCancelledAt(LocalDateTime.now());
        premiumSubscriptionRepository.save(subscription);
        userService.updateUserRoleForSystem(subscription.getUser(), UserRole.USER, "premium-active-cancellation");
        logger.info("Premium active subscription cancelled. subscriptionId={}, userId={}", subscriptionId, subscription.getUser().getId());
    }

    @Transactional(readOnly = true)
    public PremiumSubscription getSubscriptionEntity(Long subscriptionId) {
        return premiumSubscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Premium subscription not found with id: " + subscriptionId));
    }

    private PremiumSubscriptionResponse toResponse(PremiumSubscription subscription) {
        UserRole effectiveRole = userService.getUserEntityById(subscription.getUser().getId()).getRole();
        return PremiumSubscriptionResponse.from(subscription, effectiveRole);
    }
}

