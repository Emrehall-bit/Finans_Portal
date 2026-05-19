package com.emrehalli.financeportal.config.security;

import com.emrehalli.financeportal.admin.moderation.service.UserModerationService;
import com.emrehalli.financeportal.user.entity.User;
import com.emrehalli.financeportal.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.FORBIDDEN;

@ExtendWith(MockitoExtension.class)
class ModerationEnforcementFilterTest {

    @Mock
    private UserService userService;

    @Mock
    private UserModerationService userModerationService;

    private ModerationEnforcementFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ModerationEnforcementFilter(userService, userModerationService, new ObjectMapper());
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt(), List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldSkipPublicEndpoints() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/news/top");
        request.setServletPath("/api/v1/news/top");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        verify(userService, never()).getCurrentAuthenticatedUserEntity();
        verify(userModerationService, never()).assertAccessAllowed(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void shouldReturnForbiddenForBlockedUser() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(userService.getCurrentAuthenticatedUserEntity()).thenReturn(User.builder().id(5L).build());
        doThrow(new ResponseStatusException(FORBIDDEN, "User account is permanently blocked"))
                .when(userModerationService).assertAccessAllowed(5L);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(FORBIDDEN.value());
        assertThat(response.getContentAsString()).contains("User account is permanently blocked");
    }

    @Test
    void shouldAllowRequestForAccessibleUser() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(userService.getCurrentAuthenticatedUserEntity()).thenReturn(User.builder().id(5L).build());
        doNothing().when(userModerationService).assertAccessAllowed(5L);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(userModerationService).assertAccessAllowed(5L);
    }

    private Jwt jwt() {
        return new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of("sub", "test-user", "email", "test@example.com")
        );
    }
}
