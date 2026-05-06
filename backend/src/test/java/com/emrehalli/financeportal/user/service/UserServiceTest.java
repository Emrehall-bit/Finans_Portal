package com.emrehalli.financeportal.user.service;

import com.emrehalli.financeportal.common.exception.BadRequestException;
import com.emrehalli.financeportal.config.security.CurrentUser;
import com.emrehalli.financeportal.config.security.CurrentUserResolver;
import com.emrehalli.financeportal.user.dto.AdminUpdateUserRequest;
import com.emrehalli.financeportal.user.dto.UpdateUserRequest;
import com.emrehalli.financeportal.user.entity.PreferredLanguage;
import com.emrehalli.financeportal.user.entity.ThemePreference;
import com.emrehalli.financeportal.user.entity.User;
import com.emrehalli.financeportal.user.entity.UserRole;
import com.emrehalli.financeportal.user.mapper.UserMapper;
import com.emrehalli.financeportal.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private CurrentUserResolver currentUserResolver;

    @InjectMocks
    private UserService userService;

    @Test
    void updateCurrentUserProfile_updatesEditableFields() {
        CurrentUser currentUser = new CurrentUser(
                "kc-user-1",
                "user@example.com",
                "Portal User",
                UserRole.USER,
                true,
                "KEYCLOAK"
        );
        User persistedUser = User.builder()
                .id(9L)
                .keycloakId("kc-user-1")
                .email("user@example.com")
                .fullName("Portal User")
                .preferredLanguage(PreferredLanguage.TR)
                .themePreference(ThemePreference.LIGHT)
                .role(UserRole.USER)
                .createdAt(LocalDateTime.now())
                .build();

        when(currentUserResolver.resolve()).thenReturn(currentUser);
        when(userRepository.findByKeycloakId("kc-user-1")).thenReturn(Optional.of(persistedUser));
        when(userRepository.save(persistedUser)).thenReturn(persistedUser);
        doCallRealMethod().when(userMapper).applyProfileUpdate(org.mockito.ArgumentMatchers.any(User.class), org.mockito.ArgumentMatchers.any(UpdateUserRequest.class));

        userService.updateCurrentUserProfile(UpdateUserRequest.builder()
                .fullName("Updated User")
                .preferredLanguage(PreferredLanguage.EN)
                .themePreference(null)
                .build());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User savedUser = captor.getValue();
        assertEquals("Updated User", savedUser.getFullName());
        assertEquals(PreferredLanguage.EN, savedUser.getPreferredLanguage());
        assertNull(savedUser.getThemePreference());
    }

    @Test
    void updateCurrentUserProfile_whenFullNameIsBlank_throwsBadRequestException() {
        CurrentUser currentUser = new CurrentUser(
                "kc-user-1",
                "user@example.com",
                "Portal User",
                UserRole.USER,
                true,
                "KEYCLOAK"
        );
        User persistedUser = User.builder()
                .id(9L)
                .keycloakId("kc-user-1")
                .email("user@example.com")
                .fullName("Portal User")
                .role(UserRole.USER)
                .createdAt(LocalDateTime.now())
                .build();

        when(currentUserResolver.resolve()).thenReturn(currentUser);
        when(userRepository.findByKeycloakId("kc-user-1")).thenReturn(Optional.of(persistedUser));

        assertThrows(BadRequestException.class, () -> userService.updateCurrentUserProfile(UpdateUserRequest.builder()
                .fullName("   ")
                .build()));
    }

    @Test
    void updateUserAsAdmin_updatesAdminManagedFields() {
        User persistedUser = User.builder()
                .id(11L)
                .keycloakId("kc-admin-target")
                .email("target@example.com")
                .fullName("Target User")
                .preferredLanguage(PreferredLanguage.TR)
                .themePreference(ThemePreference.LIGHT)
                .role(UserRole.USER)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        when(userRepository.findById(11L)).thenReturn(Optional.of(persistedUser));
        when(userRepository.save(persistedUser)).thenReturn(persistedUser);
        when(userMapper.toResponse(persistedUser)).thenCallRealMethod();
        doCallRealMethod().when(userMapper).applyProfileUpdate(org.mockito.ArgumentMatchers.any(User.class), org.mockito.ArgumentMatchers.any(AdminUpdateUserRequest.class));

        userService.updateUserAsAdmin(11L, AdminUpdateUserRequest.builder()
                .fullName("Updated By Admin")
                .role(UserRole.ADMIN)
                .preferredLanguage(PreferredLanguage.EN)
                .themePreference(ThemePreference.DARK)
                .active(false)
                .build());

        assertEquals("Updated By Admin", persistedUser.getFullName());
        assertEquals(UserRole.ADMIN, persistedUser.getRole());
        assertEquals(PreferredLanguage.EN, persistedUser.getPreferredLanguage());
        assertEquals(ThemePreference.DARK, persistedUser.getThemePreference());
        assertEquals(false, persistedUser.isActive());
    }

    @Test
    void getAllUsers_returnsPagedResponse() {
        User persistedUser = User.builder()
                .id(3L)
                .keycloakId("kc-user-3")
                .email("page@example.com")
                .fullName("Page User")
                .role(UserRole.USER)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        when(userRepository.search(eq("page"), eq(PageRequest.of(0, 20, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt")))))
                .thenReturn(new PageImpl<>(List.of(persistedUser)));
        when(userMapper.toResponse(persistedUser)).thenCallRealMethod();

        assertEquals(1, userService.getAllUsers(0, 20, "page").getTotalElements());
    }

    @Test
    void getCurrentUserProfile_whenNoSyncNeeded_doesNotUpdateLastLoginAt() {
        CurrentUser currentUser = new CurrentUser(
                "kc-user-1",
                "user@example.com",
                "Portal User",
                UserRole.USER,
                true,
                "KEYCLOAK"
        );
        User persistedUser = User.builder()
                .id(9L)
                .keycloakId("kc-user-1")
                .email("user@example.com")
                .fullName("Portal User")
                .role(UserRole.USER)
                .createdAt(LocalDateTime.now())
                .build();

        when(currentUserResolver.resolve()).thenReturn(currentUser);
        when(userRepository.findByKeycloakId("kc-user-1")).thenReturn(Optional.of(persistedUser));
        when(userMapper.toResponse(persistedUser)).thenCallRealMethod();

        userService.getCurrentUserProfile();

        assertEquals("kc-user-1", persistedUser.getKeycloakId());
        verify(userRepository, never()).save(persistedUser);
        org.assertj.core.api.Assertions.assertThat(persistedUser.getLastLoginAt()).isNull();
    }
}



