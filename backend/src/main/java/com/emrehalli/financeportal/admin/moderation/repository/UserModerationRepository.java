package com.emrehalli.financeportal.admin.moderation.repository;

import com.emrehalli.financeportal.admin.moderation.entity.UserModeration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserModerationRepository extends JpaRepository<UserModeration, Long> {

    Optional<UserModeration> findTopByUserIdOrderByCreatedAtDescIdDesc(Long userId);
}
