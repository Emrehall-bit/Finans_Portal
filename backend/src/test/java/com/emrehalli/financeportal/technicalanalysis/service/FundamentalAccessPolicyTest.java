package com.emrehalli.financeportal.technicalanalysis.service;

import com.emrehalli.financeportal.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FundamentalAccessPolicyTest {

    private final FundamentalAccessPolicy policy = new FundamentalAccessPolicy();

    @Test
    void canAccessPremiumContent_should_be_true_for_premium_and_admin() {
        assertThat(policy.canAccessPremiumContent(UserRole.USER_PREMIUM)).isTrue();
        assertThat(policy.canAccessPremiumContent(UserRole.ADMIN)).isTrue();
    }

    @Test
    void canAccessPremiumContent_should_be_false_for_non_premium_roles() {
        assertThat(policy.canAccessPremiumContent(UserRole.USER)).isFalse();
        assertThat(policy.canAccessPremiumContent(UserRole.GUEST)).isFalse();
        assertThat(policy.canAccessPremiumContent(UserRole.SYSTEM_ENGINEER)).isFalse();
        assertThat(policy.canAccessPremiumContent(null)).isFalse();
    }

    @Test
    void requireAdmin_should_pass_for_admin() {
        assertThatCode(() -> policy.requireAdmin(UserRole.ADMIN)).doesNotThrowAnyException();
    }

    @Test
    void requireAdmin_should_throw_403_with_exact_message_for_non_admin() {
        assertThatThrownBy(() -> policy.requireAdmin(UserRole.USER))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(403);
                    assertThat(rse.getReason()).isEqualTo("Bu islem sadece admin kullanicilara aciktir");
                });
    }

    @Test
    void requireAdmin_should_throw_for_null_role() {
        assertThatThrownBy(() -> policy.requireAdmin(null))
                .isInstanceOf(ResponseStatusException.class);
    }
}
