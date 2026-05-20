package com.emrehalli.financeportal.news.repository;

import com.emrehalli.financeportal.news.entity.News;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface NewsRepository extends JpaRepository<News, Long>, JpaSpecificationExecutor<News> {

    Set<News> findByExternalIdIn(Collection<String> externalIds);

    Optional<News> findByExternalId(String externalId);

    Optional<News> findFirstByUrlAndContentEnrichedAtIsNotNullOrderByContentEnrichedAtDesc(String url);

    @Query("""
            select n
            from News n
            where n.id <> :newsId
              and coalesce(n.isKapDisclosure, false) = false
              and n.publishedAt >= :publishedAfter
              and (:category is null or lower(n.category) = lower(:category))
            order by n.publishedAt desc, n.createdAt desc
            """)
    List<News> findRecentCandidatesForRelatedNews(
            @Param("newsId") Long newsId,
            @Param("category") String category,
            @Param("publishedAfter") LocalDateTime publishedAfter
    );

    @Modifying
    long deleteByProvider(String provider);
}



