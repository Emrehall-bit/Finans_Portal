package com.emrehalli.financeportal.user.service;

import com.emrehalli.financeportal.admin.enums.AdminAuditAction;
import com.emrehalli.financeportal.admin.service.AdminAuditLogService;
import com.emrehalli.financeportal.common.exception.BadRequestException;
import com.emrehalli.financeportal.common.exception.DuplicateResourceException;
import com.emrehalli.financeportal.common.exception.ResourceNotFoundException;
import com.emrehalli.financeportal.config.security.CurrentUser;
import com.emrehalli.financeportal.config.security.CurrentUserResolver;
import com.emrehalli.financeportal.admin.dto.AdminUpdateUserRequest;
import com.emrehalli.financeportal.user.dto.CreateUserRequest;
import com.emrehalli.financeportal.user.dto.UpdateUserRequest;
import com.emrehalli.financeportal.user.dto.UpdateUserRoleRequest;
import com.emrehalli.financeportal.user.dto.UserProfileResponseDto;
import com.emrehalli.financeportal.user.dto.UserResponseDto;
import com.emrehalli.financeportal.user.entity.User;
import com.emrehalli.financeportal.user.entity.UserRole;
import com.emrehalli.financeportal.user.mapper.UserMapper;
import com.emrehalli.financeportal.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final CurrentUserResolver currentUserResolver;
    private final AdminAuditLogService adminAuditLogService;

    public UserService(UserRepository userRepository,
                       UserMapper userMapper,
                       CurrentUserResolver currentUserResolver,
                       AdminAuditLogService adminAuditLogService) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.currentUserResolver = currentUserResolver;
        this.adminAuditLogService = adminAuditLogService;
    }

    @Transactional
    public UserResponseDto createUser(CreateUserRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());
        validateUniqueUser(request.getKeycloakId(), normalizedEmail);

        User user = userMapper.toEntity(request);
        user.setEmail(normalizedEmail);
        user.setCreatedAt(LocalDateTime.now());

        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public Page<UserResponseDto> getAllUsers(int page, int size, String search) {
        validatePaging(page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        String normalizedSearch = normalizeSearch(search);

        Page<User> users = normalizedSearch == null
                ? userRepository.findAll(pageable)
                : userRepository.search(normalizedSearch, pageable);

        return users.map(userMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponseDto getUserById(Long userId) {
        return userMapper.toResponse(getUserEntityById(userId));
    }

    @Transactional
    public UserProfileResponseDto getCurrentUserProfile() {
        CurrentUser currentUser = currentUserResolver.resolve();
        User persistedUser = findOrCreateCurrentUser(currentUser);

        return toUserProfileResponse(persistedUser, currentUser);
    }

    @Transactional
    public UserProfileResponseDto updateCurrentUserProfile(UpdateUserRequest request) {
        CurrentUser currentUser = currentUserResolver.resolve();
        User persistedUser = findOrCreateCurrentUser(currentUser);

        applyProfileUpdate(persistedUser, request);
        User updatedUser = userRepository.save(persistedUser);

        return toUserProfileResponse(updatedUser, currentUser);
    }

    @Transactional
    public UserResponseDto updateUserAsAdmin(Long userId, AdminUpdateUserRequest request) {
        User user = getUserEntityById(userId);
        applyAdminUpdate(user, request);
        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponseDto deactivateUser(Long userId) {
        User user = getUserEntityById(userId);
        ensureAdminCannotDeactivateSelf(user);
        user.setActive(false);
        User updatedUser = userRepository.save(user);
        User actor = getCurrentAuthenticatedUserEntity();
        adminAuditLogService.log(
                actor.getId(),
                updatedUser.getId(),
                AdminAuditAction.USER_DEACTIVATED,
                "User deactivated by admin",
                "active=false"
        );
        return userMapper.toResponse(updatedUser);
    }

    @Transactional
    public UserResponseDto activateUser(Long userId) {
        User user = getUserEntityById(userId);
        user.setActive(true);
        User updatedUser = userRepository.save(user);
        User actor = getCurrentAuthenticatedUserEntity();
        adminAuditLogService.log(
                actor.getId(),
                updatedUser.getId(),
                AdminAuditAction.USER_ACTIVATED,
                "User activated by admin",
                "active=true"
        );
        return userMapper.toResponse(updatedUser);
    }

    @Transactional
    public UserResponseDto updateUserRole(Long userId, UpdateUserRoleRequest request) {
        User user = getUserEntityById(userId);
        ensureAdminCannotDemoteSelf(user, request.getRole());
        UserRole previousRole = user.getRole();
        user.setRole(request.getRole());
        User updatedUser = userRepository.save(user);
        User actor = getCurrentAuthenticatedUserEntity();
        adminAuditLogService.log(
                actor.getId(),
                updatedUser.getId(),
                AdminAuditAction.USER_ROLE_CHANGED,
                "User role changed by admin",
                "previousRole=" + previousRole + ",newRole=" + updatedUser.getRole()
        );
        return userMapper.toResponse(updatedUser);
    }

    @Transactional
    public User updateUserRoleForSystem(User user, UserRole role, String reason) {
        User persistedUser = getUserEntityById(user.getId());
        UserRole previousRole = persistedUser.getRole();
        persistedUser.setRole(role);
        User updatedUser = userRepository.save(persistedUser);
        adminAuditLogService.log(
                null,
                updatedUser.getId(),
                AdminAuditAction.USER_ROLE_CHANGED,
                "User role changed by system",
                "reason=" + reason + ",previousRole=" + previousRole + ",newRole=" + updatedUser.getRole()
        );
        return updatedUser;
    }

    @Transactional
    public void deleteCurrentUserAccount() {
        CurrentUser currentUser = currentUserResolver.resolve();
        User user = findOrCreateCurrentUser(currentUser);
        userRepository.delete(user);
    }

    @Transactional
    public User getCurrentAuthenticatedUserEntity() {
        CurrentUser currentUser = currentUserResolver.resolve();
        return findOrCreateCurrentUser(currentUser);
    }

    private UserProfileResponseDto toUserProfileResponse(User user, CurrentUser currentUser) {
        return UserProfileResponseDto.builder()
                .user(userMapper.toResponse(user))
                .authenticated(currentUser.authenticated())
                .authProvider(currentUser.authProvider())
                .build();
    }

    @Transactional(readOnly = true)
    public User getUserEntityById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
    }

    private User findOrCreateCurrentUser(CurrentUser currentUser) {
        Optional<User> existingUser = findExistingUser(currentUser);
        if (existingUser.isPresent()) {
            User user = existingUser.get();
            ensureUserIsActive(user);
            boolean updated = applyCurrentUserData(user, currentUser);
            if (updated) {
                user.setLastLoginAt(LocalDateTime.now());
                return userRepository.save(user);
            }
            return user;
        }

        if (currentUser.email() == null || currentUser.email().isBlank()) {
            throw new BadRequestException("Current user email is required to create a local user profile");
        }

        User user = User.builder()
                .keycloakId(currentUser.keycloakId())
                .email(normalizeEmail(currentUser.email()))
                .fullName(resolveFullName(currentUser))
                .role(currentUser.role() != null ? currentUser.role() : UserRole.USER)
                .createdAt(LocalDateTime.now())
                .lastLoginAt(LocalDateTime.now())
                .build();

        User savedUser = userRepository.save(user);
        ensureUserIsActive(savedUser);
        return savedUser;
    }

    private void ensureUserIsActive(User user) {
        if (!user.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is deactivated");
        }
    }

    private void ensureAdminCannotDeactivateSelf(User user) {
        CurrentUser currentUser = currentUserResolver.resolve();
        if (currentUser.keycloakId() != null
                && user.getKeycloakId() != null
                && currentUser.keycloakId().equals(user.getKeycloakId())) {
            throw new BadRequestException("Admin users cannot deactivate their own account");
        }
    }

    private void ensureAdminCannotDemoteSelf(User user, UserRole targetRole) {
        if (targetRole != UserRole.USER) {
            return;
        }

        CurrentUser currentUser = currentUserResolver.resolve();
        if (currentUser.keycloakId() != null
                && user.getKeycloakId() != null
                && currentUser.keycloakId().equals(user.getKeycloakId())) {
            throw new BadRequestException("Admin users cannot remove their own admin role");
        }
    }

    private Optional<User> findExistingUser(CurrentUser currentUser) {
        if (currentUser.keycloakId() != null && !currentUser.keycloakId().isBlank()) {
            Optional<User> userByKeycloakId = userRepository.findByKeycloakId(currentUser.keycloakId());
            if (userByKeycloakId.isPresent()) {
                return userByKeycloakId;
            }
        }

        if (currentUser.email() != null && !currentUser.email().isBlank()) {
            return userRepository.findByEmail(normalizeEmail(currentUser.email()));
        }

        return Optional.empty();
    }

    private boolean applyCurrentUserData(User user, CurrentUser currentUser) {
        boolean updated = false;

        if (currentUser.keycloakId() != null && !currentUser.keycloakId().isBlank()
                && !currentUser.keycloakId().equals(user.getKeycloakId())) {
            userRepository.findByKeycloakId(currentUser.keycloakId())
                    .filter(existingUser -> !existingUser.getId().equals(user.getId()))
                    .ifPresent(existingUser -> {
                        throw new DuplicateResourceException("A user with this Keycloak ID already exists");
                    });
            user.setKeycloakId(currentUser.keycloakId());
            updated = true;
        }

        String normalizedEmail = normalizeEmail(currentUser.email());
        if (normalizedEmail != null && !normalizedEmail.equals(user.getEmail())) {
            userRepository.findByEmail(normalizedEmail)
                    .filter(existingUser -> !existingUser.getId().equals(user.getId()))
                    .ifPresent(existingUser -> {
                        throw new DuplicateResourceException("A user with this email already exists");
                    });
            user.setEmail(normalizedEmail);
            updated = true;
        }

        String resolvedFullName = resolveFullName(currentUser);
        if (!resolvedFullName.equals(user.getFullName())) {
            user.setFullName(resolvedFullName);
            updated = true;
        }

        if (currentUser.role() != null && currentUser.role() != user.getRole()) {
            user.setRole(currentUser.role());
            updated = true;
        }

        return updated;
    }

    private void applyProfileUpdate(User user, UpdateUserRequest request) {
        String resolvedFullName = normalizeFullName(request.getFullName());
        UpdateUserRequest normalizedRequest = UpdateUserRequest.builder()
                .fullName(resolvedFullName != null ? resolvedFullName : user.getFullName())
                .preferredLanguage(request.getPreferredLanguage())
                .themePreference(request.getThemePreference())
                .build();
        userMapper.applyProfileUpdate(user, normalizedRequest);
    }

    private void applyAdminUpdate(User user, AdminUpdateUserRequest request) {
        String resolvedFullName = normalizeFullName(request.getFullName());
        AdminUpdateUserRequest normalizedRequest = AdminUpdateUserRequest.builder()
                .fullName(resolvedFullName != null ? resolvedFullName : user.getFullName())
                .role(request.getRole())
                .preferredLanguage(request.getPreferredLanguage())
                .themePreference(request.getThemePreference())
                .active(request.getActive())
                .build();
        userMapper.applyProfileUpdate(user, normalizedRequest);
    }

    private void validateUniqueUser(String keycloakId, String email) {
        if (email != null && userRepository.findByEmail(email).isPresent()) {
            throw new DuplicateResourceException("A user with this email already exists");
        }

        if (keycloakId != null && !keycloakId.isBlank() && userRepository.findByKeycloakId(keycloakId).isPresent()) {
            throw new DuplicateResourceException("A user with this Keycloak ID already exists");
        }
    }

    private String resolveFullName(CurrentUser currentUser) {
        if (currentUser.fullName() != null && !currentUser.fullName().isBlank()) {
            return currentUser.fullName().trim();
        }

        if (currentUser.email() != null && !currentUser.email().isBlank()) {
            return currentUser.email().trim();
        }

        throw new BadRequestException("Current user full name could not be resolved");
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private String normalizeFullName(String fullName) {
        if (fullName == null) {
            return null;
        }

        String trimmed = fullName.trim();
        if (trimmed.isEmpty()) {
            throw new BadRequestException("Full name cannot be blank");
        }

        return trimmed;
    }

    private String normalizeSearch(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void validatePaging(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("page must be greater than or equal to 0");
        }
        if (size < 1 || size > 100) {
            throw new BadRequestException("size must be between 1 and 100");
        }
    }
}


