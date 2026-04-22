package com.emrehalli.financeportal.user.service;

import com.emrehalli.financeportal.common.exception.BadRequestException;
import com.emrehalli.financeportal.common.exception.DuplicateResourceException;
import com.emrehalli.financeportal.common.exception.ResourceNotFoundException;
import com.emrehalli.financeportal.config.security.CurrentUser;
import com.emrehalli.financeportal.config.security.CurrentUserResolver;
import com.emrehalli.financeportal.user.dto.CreateUserRequest;
import com.emrehalli.financeportal.user.dto.UserProfileResponseDto;
import com.emrehalli.financeportal.user.dto.UserResponseDto;
import com.emrehalli.financeportal.user.entity.User;
import com.emrehalli.financeportal.user.entity.UserRole;
import com.emrehalli.financeportal.user.mapper.UserMapper;
import com.emrehalli.financeportal.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final CurrentUserResolver currentUserResolver;

    public UserService(UserRepository userRepository,
                       UserMapper userMapper,
                       CurrentUserResolver currentUserResolver) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.currentUserResolver = currentUserResolver;
    }

    public UserResponseDto createUser(CreateUserRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());
        validateUniqueUser(request.getKeycloakId(), normalizedEmail);

        User user = userMapper.toEntity(request);
        user.setEmail(normalizedEmail);
        user.setCreatedAt(LocalDateTime.now());

        return userMapper.toResponse(userRepository.save(user));
    }

    public List<UserResponseDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(userMapper::toResponse)
                .toList();
    }

    public UserResponseDto getUserById(Long userId) {
        return userMapper.toResponse(getUserEntityById(userId));
    }

    public UserProfileResponseDto getCurrentUserProfile() {
        CurrentUser currentUser = currentUserResolver.resolve();
        User persistedUser = findOrCreateCurrentUser(currentUser);

        return UserProfileResponseDto.builder()
                .user(userMapper.toResponse(persistedUser))
                .authenticated(currentUser.authenticated())
                .authProvider(currentUser.authProvider())
                .build();
    }

    public User getUserEntityById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
    }

    private User findOrCreateCurrentUser(CurrentUser currentUser) {
        Optional<User> existingUser = findExistingUser(currentUser);
        if (existingUser.isPresent()) {
            User user = existingUser.get();
            boolean updated = applyCurrentUserData(user, currentUser);
            return updated ? userRepository.save(user) : user;
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
                .build();

        return userRepository.save(user);
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
}
