package com.emrehalli.financeportal.user.mapper;

import com.emrehalli.financeportal.admin.dto.AdminUpdateUserRequest;
import com.emrehalli.financeportal.user.dto.CreateUserRequest;
import com.emrehalli.financeportal.user.dto.ProfileNoteItem;
import com.emrehalli.financeportal.user.dto.UpdateUserRequest;
import com.emrehalli.financeportal.user.dto.UserResponseDto;
import com.emrehalli.financeportal.user.entity.PreferredLanguage;
import com.emrehalli.financeportal.user.entity.ThemePreference;
import com.emrehalli.financeportal.user.entity.User;
import com.emrehalli.financeportal.user.entity.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UserMapperTest {

    private final UserMapper mapper = new UserMapper(new ObjectMapper());

    @Test
    void toEntity_trims_and_lowercases_email() {
        CreateUserRequest request = CreateUserRequest.builder()
                .keycloakId("kc-1")
                .fullName("Ada Lovelace")
                .email("  Ada.Lovelace@EXAMPLE.COM  ")
                .role(UserRole.USER)
                .preferredLanguage(PreferredLanguage.TR)
                .themePreference(ThemePreference.DARK)
                .build();

        User entity = mapper.toEntity(request);

        assertThat(entity.getEmail()).isEqualTo("ada.lovelace@example.com");
        assertThat(entity.getKeycloakId()).isEqualTo("kc-1");
        assertThat(entity.getRole()).isEqualTo(UserRole.USER);
    }

    @Test
    void toEntity_defaults_role_to_user_when_request_role_is_null() {
        CreateUserRequest request = CreateUserRequest.builder()
                .keycloakId("kc-2")
                .fullName("No Role")
                .email("norole@example.com")
                .role(null)
                .build();

        User entity = mapper.toEntity(request);

        assertThat(entity.getRole()).isEqualTo(UserRole.USER);
    }

    @Test
    void applyProfileUpdate_admin_request_updates_only_non_null_fields() {
        User user = User.builder()
                .id(1L)
                .fullName("Old Name")
                .role(UserRole.USER)
                .preferredLanguage(PreferredLanguage.TR)
                .themePreference(ThemePreference.DARK)
                .active(true)
                .build();

        AdminUpdateUserRequest request = AdminUpdateUserRequest.builder()
                .fullName("New Name")
                .role(null)
                .preferredLanguage(null)
                .themePreference(null)
                .active(null)
                .build();

        mapper.applyProfileUpdate(user, request);

        assertThat(user.getFullName()).isEqualTo("New Name");
        // null fields in the admin request must not overwrite the existing values
        assertThat(user.getRole()).isEqualTo(UserRole.USER);
        assertThat(user.getPreferredLanguage()).isEqualTo(PreferredLanguage.TR);
        assertThat(user.getThemePreference()).isEqualTo(ThemePreference.DARK);
        assertThat(user.isActive()).isTrue();
    }

    @Test
    void applyProfileUpdate_admin_request_overwrites_fields_when_provided() {
        User user = User.builder()
                .id(1L)
                .fullName("Old Name")
                .role(UserRole.USER)
                .preferredLanguage(PreferredLanguage.TR)
                .themePreference(ThemePreference.DARK)
                .active(true)
                .build();

        AdminUpdateUserRequest request = AdminUpdateUserRequest.builder()
                .fullName("Updated Name")
                .role(UserRole.ADMIN)
                .preferredLanguage(PreferredLanguage.EN)
                .themePreference(ThemePreference.LIGHT)
                .active(false)
                .build();

        mapper.applyProfileUpdate(user, request);

        assertThat(user.getFullName()).isEqualTo("Updated Name");
        assertThat(user.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(user.getPreferredLanguage()).isEqualTo(PreferredLanguage.EN);
        assertThat(user.getThemePreference()).isEqualTo(ThemePreference.LIGHT);
        assertThat(user.isActive()).isFalse();
    }

    @Test
    void applyProfileUpdate_profile_request_preserves_existing_notes_when_request_notes_is_null() {
        User user = User.builder()
                .id(1L)
                .fullName("Old Name")
                .profileNote("[{\"id\":\"1\",\"content\":\"Existing note\"}]")
                .build();

        UpdateUserRequest request = UpdateUserRequest.builder()
                .fullName("New Name")
                .preferredLanguage(PreferredLanguage.EN)
                .themePreference(ThemePreference.SYSTEM)
                .notes(null)
                .build();

        mapper.applyProfileUpdate(user, request);

        assertThat(user.getFullName()).isEqualTo("New Name");
        assertThat(user.getPreferredLanguage()).isEqualTo(PreferredLanguage.EN);
        assertThat(user.getThemePreference()).isEqualTo(ThemePreference.SYSTEM);
        assertThat(user.getProfileNote()).isEqualTo("[{\"id\":\"1\",\"content\":\"Existing note\"}]");
    }

    @Test
    void applyProfileUpdate_profile_request_replaces_notes_when_provided() {
        User user = User.builder()
                .id(1L)
                .fullName("Old Name")
                .profileNote("[{\"id\":\"1\",\"content\":\"Existing note\"}]")
                .build();

        ProfileNoteItem newNote = new ProfileNoteItem("2", "New note", "2026-06-08T10:00:00", "2026-06-08T10:00:00", true);
        UpdateUserRequest request = UpdateUserRequest.builder()
                .fullName("Old Name")
                .notes(List.of(newNote))
                .build();

        mapper.applyProfileUpdate(user, request);

        assertThat(user.getProfileNote()).contains("\"content\":\"New note\"").doesNotContain("Existing note");
    }

    @Test
    void toResponse_maps_role_language_theme_and_notes() {
        User user = User.builder()
                .id(7L)
                .keycloakId("kc-7")
                .fullName("Grace Hopper")
                .email("grace@example.com")
                .role(UserRole.ADMIN)
                .preferredLanguage(PreferredLanguage.EN)
                .themePreference(ThemePreference.DARK)
                .createdAt(LocalDateTime.of(2026, 1, 1, 9, 0))
                .updatedAt(LocalDateTime.of(2026, 6, 1, 9, 0))
                .active(true)
                .profileNote("[{\"id\":\"1\",\"content\":\"Pinned note\",\"pinned\":true}]")
                .build();

        UserResponseDto response = mapper.toResponse(user);

        assertThat(response.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(response.getPreferredLanguage()).isEqualTo(PreferredLanguage.EN);
        assertThat(response.getThemePreference()).isEqualTo(ThemePreference.DARK);
        assertThat(response.isActive()).isTrue();
        assertThat(response.getNotes()).hasSize(1);
        assertThat(response.getNotes().getFirst().getContent()).isEqualTo("Pinned note");
        assertThat(response.getNotes().getFirst().getPinned()).isTrue();
    }

    @Test
    void toResponse_returns_empty_notes_when_profile_note_is_blank() {
        User user = User.builder()
                .id(8L)
                .role(UserRole.USER)
                .profileNote(null)
                .build();

        UserResponseDto response = mapper.toResponse(user);

        assertThat(response.getNotes()).isEmpty();
    }
}
