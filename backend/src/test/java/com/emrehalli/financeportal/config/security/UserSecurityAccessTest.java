package com.emrehalli.financeportal.config.security;

import com.emrehalli.financeportal.user.controller.UserController;
import com.emrehalli.financeportal.user.dto.CreateUserRequest;
import com.emrehalli.financeportal.user.dto.UserResponseDto;
import com.emrehalli.financeportal.user.entity.UserRole;
import com.emrehalli.financeportal.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, KeycloakJwtRoleConverter.class})
class UserSecurityAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private ResourceAccessManager resourceAccessManager;

    @Test
    void userWithoutAdminRole_cannotAccessAdminEndpoints() throws Exception {
        CreateUserRequest request = CreateUserRequest.builder()
                .fullName("Regular User")
                .email("user@example.com")
                .build();

        mockMvc.perform(get("/api/v1/users/admin")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities(() -> "ROLE_USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/users")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities(() -> "ROLE_USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminUser_canAccessAdminEndpoints() throws Exception {
        CreateUserRequest request = CreateUserRequest.builder()
                .keycloakId("kc-admin-1")
                .fullName("Admin User")
                .email("admin@example.com")
                .role(UserRole.ADMIN)
                .build();

        when(userService.getAllUsers()).thenReturn(List.of(
                UserResponseDto.builder()
                        .id(1L)
                        .keycloakId("kc-admin-1")
                        .fullName("Admin User")
                        .email("admin@example.com")
                        .role(UserRole.ADMIN)
                        .createdAt(LocalDateTime.now())
                        .build()
        ));
        when(userService.createUser(any(CreateUserRequest.class))).thenReturn(
                UserResponseDto.builder()
                        .id(2L)
                        .keycloakId("kc-admin-2")
                        .fullName("Created Admin")
                        .email("created-admin@example.com")
                        .role(UserRole.ADMIN)
                        .createdAt(LocalDateTime.now())
                        .build()
        );

        mockMvc.perform(get("/api/v1/users/admin")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/users")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities(() -> "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}



