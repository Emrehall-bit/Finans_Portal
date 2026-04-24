package com.emrehalli.financeportal.user.service;

import com.emrehalli.financeportal.common.exception.BadRequestException;
import com.emrehalli.financeportal.config.security.CurrentUser;
import com.emrehalli.financeportal.config.security.CurrentUserResolver;
import com.emrehalli.financeportal.user.dto.UpdateUserRequest;
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

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
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
                .preferredLanguage("tr")
                .themePreference("light")
                .role(UserRole.USER)
                .createdAt(LocalDateTime.now())
                .build();

        when(currentUserResolver.resolve()).thenReturn(currentUser);
        when(userRepository.findByKeycloakId("kc-user-1")).thenReturn(Optional.of(persistedUser));
        when(userRepository.save(persistedUser)).thenReturn(persistedUser);

        userService.updateCurrentUserProfile(UpdateUserRequest.builder()
                .fullName("Updated User")
                .preferredLanguage("en")
                .themePreference("")
                .build());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User savedUser = captor.getValue();
        assertEquals("Updated User", savedUser.getFullName());
        assertEquals("en", savedUser.getPreferredLanguage());
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
}



