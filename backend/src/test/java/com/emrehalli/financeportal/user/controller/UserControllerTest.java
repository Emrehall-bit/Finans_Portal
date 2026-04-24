package com.emrehalli.financeportal.user.controller;

import com.emrehalli.financeportal.config.security.KeycloakJwtRoleConverter;
import com.emrehalli.financeportal.config.security.ResourceAccessManager;
import com.emrehalli.financeportal.config.security.SecurityConfig;
import com.emrehalli.financeportal.user.dto.UpdateUserRequest;
import com.emrehalli.financeportal.user.dto.UserProfileResponseDto;
import com.emrehalli.financeportal.user.dto.UserResponseDto;
import com.emrehalli.financeportal.user.entity.UserRole;
import com.emrehalli.financeportal.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, KeycloakJwtRoleConverter.class})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private ResourceAccessManager resourceAccessManager;

    @Test
    void updateCurrentUserProfile_whenAuthenticated_returnsUpdatedProfile() throws Exception {
        UserProfileResponseDto response = UserProfileResponseDto.builder()
                .authenticated(true)
                .authProvider("KEYCLOAK")
                .user(UserResponseDto.builder()
                        .id(5L)
                        .keycloakId("kc-user-1")
                        .fullName("Updated User")
                        .email("user@example.com")
                        .preferredLanguage("en")
                        .themePreference("light")
                        .role(UserRole.USER)
                        .createdAt(LocalDateTime.now())
                        .build())
                .build();

        when(userService.updateCurrentUserProfile(any(UpdateUserRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/users/me")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities(() -> "ROLE_USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpdateUserRequest.builder()
                                .fullName("Updated User")
                                .preferredLanguage("en")
                                .themePreference("light")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.fullName").value("Updated User"))
                .andExpect(jsonPath("$.data.user.preferredLanguage").value("en"))
                .andExpect(jsonPath("$.data.user.themePreference").value("light"));
    }
}



