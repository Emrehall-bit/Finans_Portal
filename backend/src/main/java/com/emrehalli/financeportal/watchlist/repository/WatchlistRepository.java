package com.emrehalli.financeportal.watchlist.repository;

import com.emrehalli.financeportal.watchlist.entity.Watchlist;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WatchlistRepository extends JpaRepository<Watchlist, Long> {

    @EntityGraph(attributePaths = "user")
    List<Watchlist> findByUserId(Long userId);

    boolean existsByUserIdAndInstrumentCodeIgnoreCase(Long userId, String instrumentCode);
}
