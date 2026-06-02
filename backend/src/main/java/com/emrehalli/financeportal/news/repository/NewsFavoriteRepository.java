package com.emrehalli.financeportal.news.repository;

import com.emrehalli.financeportal.news.entity.NewsFavorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NewsFavoriteRepository extends JpaRepository<NewsFavorite, Long> {

    Optional<NewsFavorite> findByUserIdAndNewsId(Long userId, Long newsId);

    boolean existsByUserIdAndNewsId(Long userId, Long newsId);

    void deleteByUserIdAndNewsId(Long userId, Long newsId);

    List<NewsFavorite> findByUserIdOrderByCreatedAtDesc(Long userId);
}
