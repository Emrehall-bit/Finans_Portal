package com.emrehalli.financeportal.admin.moderation.service;

import com.emrehalli.financeportal.admin.moderation.entity.UserModeration;
import com.emrehalli.financeportal.admin.moderation.enums.ModerationStatus;
import com.emrehalli.financeportal.admin.moderation.repository.UserModerationRepository;
import com.emrehalli.financeportal.admin.service.AdminAuditLogService;
import com.emrehalli.financeportal.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserModerationServiceTest {

    @Mock
    private UserModerationRepository userModerationRepository;

    @Mock
    private UserService userService;

    @Mock
    private AdminAuditLogService adminAuditLogService;

    private com.emrehalli.financeportal.admin.moderation.service.UserModerationService userModerationService;

    @BeforeEach
    void setUp() {
        userModerationService = new com.emrehalli.financeportal.admin.moderation.service.UserModerationService(userModerationRepository, userService, adminAuditLogService);
    }

    @Test
    void shouldRejectPermanentlyBlockedUser() {
        when(userModerationRepository.findTopByUserIdOrderByCreatedAtDescIdDesc(10L))
                .thenReturn(Optional.of(UserModeration.builder()
                        .status(ModerationStatus.PERM_BLOCKED)
                        .createdAt(LocalDateTime.now())
                        .build()));

        assertThatThrownBy(() -> userModerationService.assertAccessAllowed(10L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException ex = (ResponseStatusException) exception;
                    assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.FORBIDDEN.value());
                    assertThat(ex.getReason()).isEqualTo("User account is permanently blocked");
                });
    }

    @Test
    void shouldRejectTemporarilyBlockedUserWhenBlockStillActive() {
        when(userModerationRepository.findTopByUserIdOrderByCreatedAtDescIdDesc(10L))
                .thenReturn(Optional.of(UserModeration.builder()
                        .status(ModerationStatus.TEMP_BLOCKED)
                        .blockedUntil(LocalDateTime.now().plusHours(4))
                        .createdAt(LocalDateTime.now())
                        .build()));

        assertThatThrownBy(() -> userModerationService.assertAccessAllowed(10L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException ex = (ResponseStatusException) exception;
                    assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.FORBIDDEN.value());
                    assertThat(ex.getReason()).isEqualTo("User account is temporarily blocked");
                });
    }

    @Test
    void shouldAllowTemporarilyBlockedUserWhenBlockExpired() {
        when(userModerationRepository.findTopByUserIdOrderByCreatedAtDescIdDesc(10L))
                .thenReturn(Optional.of(UserModeration.builder()
                        .status(ModerationStatus.TEMP_BLOCKED)
                        .blockedUntil(LocalDateTime.now().minusMinutes(1))
                        .createdAt(LocalDateTime.now())
                        .build()));

        assertThatCode(() -> userModerationService.assertAccessAllowed(10L))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAllowActiveUser() {
        when(userModerationRepository.findTopByUserIdOrderByCreatedAtDescIdDesc(10L))
                .thenReturn(Optional.of(UserModeration.builder()
                        .status(ModerationStatus.ACTIVE)
                        .createdAt(LocalDateTime.now())
                        .build()));

        assertThatCode(() -> userModerationService.assertAccessAllowed(10L))
                .doesNotThrowAnyException();
    }
}
