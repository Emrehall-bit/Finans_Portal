package com.emrehalli.financeportal.watchlist.service;

import com.emrehalli.financeportal.common.exception.DuplicateResourceException;
import com.emrehalli.financeportal.common.exception.ResourceNotFoundException;
import com.emrehalli.financeportal.user.entity.User;
import com.emrehalli.financeportal.user.repository.UserRepository;
import com.emrehalli.financeportal.watchlist.dto.WatchlistResponseDto;
import com.emrehalli.financeportal.watchlist.entity.Watchlist;
import com.emrehalli.financeportal.watchlist.repository.WatchlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WatchlistServiceTest {

    private WatchlistRepository watchlistRepository;
    private UserRepository userRepository;
    private WatchlistService service;

    @BeforeEach
    void setUp() {
        watchlistRepository = mock(WatchlistRepository.class);
        userRepository = mock(UserRepository.class);
        service = new WatchlistService(watchlistRepository, userRepository);
    }

    @Test
    void addFavorite_rejects_duplicate_for_same_user_and_instrument() {
        Long userId = 1L;
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId)));
        when(watchlistRepository.existsByUserIdAndInstrumentCodeIgnoreCase(userId, "THYAO")).thenReturn(true);

        assertThatThrownBy(() -> service.addFavorite(userId, "thyao"))
                .isInstanceOf(DuplicateResourceException.class);

        verify(watchlistRepository, never()).save(any());
    }

    @Test
    void addFavorite_saves_when_not_duplicate() {
        Long userId = 1L;
        User user = user(userId);
        LocalDateTime now = LocalDateTime.of(2026, 6, 8, 10, 0);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(watchlistRepository.existsByUserIdAndInstrumentCodeIgnoreCase(userId, "THYAO")).thenReturn(false);
        when(watchlistRepository.save(any(Watchlist.class))).thenAnswer(invocation -> {
            Watchlist toSave = invocation.getArgument(0);
            return Watchlist.builder()
                    .id(10L)
                    .user(toSave.getUser())
                    .instrumentCode(toSave.getInstrumentCode())
                    .createdAt(toSave.getCreatedAt())
                    .build();
        });

        WatchlistResponseDto response = service.addFavorite(userId, "thyao");

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getInstrumentCode()).isEqualTo("THYAO");

        verify(watchlistRepository).save(argThat(w -> w != null && "THYAO".equals(w.getInstrumentCode())));
    }

    @Test
    void getUserWatchlist_returns_user_items_only() {
        Long userId = 1L;
        User user = user(userId);
        when(userRepository.existsById(userId)).thenReturn(true);
        when(watchlistRepository.findByUserId(userId)).thenReturn(List.of(
                Watchlist.builder().id(1L).user(user).instrumentCode("THYAO").createdAt(LocalDateTime.now()).build(),
                Watchlist.builder().id(2L).user(user).instrumentCode("GARAN").createdAt(LocalDateTime.now()).build()
        ));

        List<WatchlistResponseDto> result = service.getUserWatchlist(userId);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(WatchlistResponseDto::getInstrumentCode).containsExactly("THYAO", "GARAN");
        assertThat(result).allMatch(item -> item.getUserId().equals(userId));
    }

    @Test
    void getUserWatchlist_throws_when_user_does_not_exist() {
        Long userId = 99L;
        when(userRepository.existsById(userId)).thenReturn(false);

        assertThatThrownBy(() -> service.getUserWatchlist(userId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(watchlistRepository, never()).findByUserId(any());
    }

    @Test
    void removeFavorite_deletes_existing_item() {
        Long watchlistId = 5L;
        Watchlist watchlist = Watchlist.builder().id(watchlistId).user(user(1L)).instrumentCode("THYAO").build();
        when(watchlistRepository.findById(watchlistId)).thenReturn(Optional.of(watchlist));

        service.removeFavorite(watchlistId);

        verify(watchlistRepository).delete(watchlist);
    }

    @Test
    void removeFavorite_throws_when_item_not_found() {
        Long watchlistId = 404L;
        when(watchlistRepository.findById(watchlistId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeFavorite(watchlistId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(watchlistRepository, never()).delete(any());
    }

    private User user(Long id) {
        return User.builder().id(id).keycloakId("keycloak-" + id).email("user" + id + "@test.com").build();
    }
}
