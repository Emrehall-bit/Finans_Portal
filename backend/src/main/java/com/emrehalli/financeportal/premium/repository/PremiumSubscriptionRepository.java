package com.emrehalli.financeportal.premium.repository;

import com.emrehalli.financeportal.premium.entity.PremiumSubscription;
import com.emrehalli.financeportal.premium.entity.PremiumSubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PremiumSubscriptionRepository extends JpaRepository<PremiumSubscription, Long> {

    Optional<PremiumSubscription> findTopByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByUserIdAndStatusIn(Long userId, Collection<PremiumSubscriptionStatus> statuses);

    List<PremiumSubscription> findAllByStatusOrderByCreatedAtDesc(PremiumSubscriptionStatus status);

    List<PremiumSubscription> findAllByStatusInOrderByCreatedAtDesc(Collection<PremiumSubscriptionStatus> statuses);
}




