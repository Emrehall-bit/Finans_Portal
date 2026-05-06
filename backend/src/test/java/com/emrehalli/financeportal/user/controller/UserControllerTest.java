package com.emrehalli.financeportal.user.controller;

import com.emrehalli.financeportal.common.exception.GlobalExceptionHandler;
import com.emrehalli.financeportal.common.i18n.AppMessageSource;
import com.emrehalli.financeportal.config.security.KeycloakJwtRoleConverter;
import com.emrehalli.financeportal.config.security.ResourceAccessManager;
import com.emrehalli.financeportal.config.security.SecurityConfig;
import com.emrehalli.financeportal.user.dto.AdminUpdateUserRequest;
import com.emrehalli.financeportal.user.dto.UpdateUserRequest;
import com.emrehalli.financeportal.user.dto.UserResponseDto;
import com.emrehalli.financeportal.user.entity.PreferredLanguage;
import com.emrehalli.financeportal.user.entity.ThemePreference;
import com.emrehalli.financeportal.user.dto.UserProfileResponseDto;
import com.emrehalli.financeportal.user.entity.UserRole;
import com.emrehalli.financeportal.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, KeycloakJwtRoleConverter.class, GlobalExceptionHandler.class})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private ResourceAccessManager resourceAccessManager;

    @MockBean
    private AppMessageSource appMessageSource;

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
                        .preferredLanguage(PreferredLanguage.EN)
                        .themePreference(ThemePreference.LIGHT)
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
                                .preferredLanguage(PreferredLanguage.EN)
                                .themePreference(ThemePreference.LIGHT)
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.fullName").value("Updated User"))
                .andExpect(jsonPath("$.data.user.preferredLanguage").value("en"))
                .andExpect(jsonPath("$.data.user.themePreference").value("light"));
    }

    @Test
    void getAllUsers_whenAdmin_returnsPagedUsers() throws Exception {
        when(userService.getAllUsers(0, 20, "portal")).thenReturn(new PageImpl<>(List.of(
                UserResponseDto.builder()
                        .id(1L)
                        .fullName("Portal Admin")
                        .email("admin@example.com")
                        .role(UserRole.ADMIN)
                        .active(true)
                        .createdAt(LocalDateTime.now())
                        .build()
        )));

        mockMvc.perform(get("/api/v1/users/admin")
                        .param("page", "0")
                        .param("size", "20")
                        .param("search", "portal")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].fullName").value("Portal Admin"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void updateUserAsAdmin_whenAdmin_returnsUpdatedUser() throws Exception {
        when(userService.updateUserAsAdmin(any(), any(AdminUpdateUserRequest.class))).thenReturn(
                UserResponseDto.builder()
                        .id(7L)
                        .fullName("Updated By Admin")
                        .email("user@example.com")
                        .role(UserRole.ADMIN)
                        .preferredLanguage(PreferredLanguage.EN)
                        .themePreference(ThemePreference.DARK)
                        .active(false)
                        .createdAt(LocalDateTime.now())
                        .build()
        );

        mockMvc.perform(put("/api/v1/users/admin/7")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities(() -> "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminUpdateUserRequest.builder()
                                .fullName("Updated By Admin")
                                .role(UserRole.ADMIN)
                                .preferredLanguage(PreferredLanguage.EN)
                                .themePreference(ThemePreference.DARK)
                                .active(false)
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.active").value(false));
    }

    @Test
    void adminEndpoint_whenNonAdmin_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/users/admin")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities(() -> "ROLE_USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateCurrentUserProfile_whenInvalidThemePreference_rejected() throws Exception {
        mockMvc.perform(put("/api/v1/users/me")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities(() -> "ROLE_USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Updated User",
                                  "preferredLanguage": "xx",
                                  "themePreference": "neon"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void deactivateAndActivateUser_whenAdmin_returnUpdatedState() throws Exception {
        when(userService.deactivateUser(12L)).thenReturn(UserResponseDto.builder()
                .id(12L)
                .fullName("Inactive User")
                .email("inactive@example.com")
                .role(UserRole.USER)
                .active(false)
                .createdAt(LocalDateTime.now())
                .build());
        when(userService.activateUser(12L)).thenReturn(UserResponseDto.builder()
                .id(12L)
                .fullName("Inactive User")
                .email("inactive@example.com")
                .role(UserRole.USER)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build());

        mockMvc.perform(patch("/api/v1/users/admin/12/deactivate")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));

        mockMvc.perform(patch("/api/v1/users/admin/12/activate")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true));
    }
}

