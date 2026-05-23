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
import org.springframework.data.domain.Pageable;
import java.util.Set;

public interface NewsRepository extends JpaRepository<News, Long>, JpaSpecificationExecutor<News> {

    Set<News> findByExternalIdIn(Collection<String> externalIds);

    Set<News> findByUrlIn(Collection<String> urls);

    Optional<News> findByExternalId(String externalId);

    Optional<News> findFirstByUrlAndContentEnrichedAtIsNotNullOrderByContentEnrichedAtDesc(String url);

    @Query("""
            select n
            from News n
            where n.id <> :newsId
              and coalesce(n.isKapDisclosure, false) = false
              and n.publishedAt >= :publishedAfter
            order by n.publishedAt desc, n.createdAt desc
            """)
    List<News> findRecentCandidatesForRelatedNews(
            @Param("newsId") Long newsId,
            @Param("publishedAfter") LocalDateTime publishedAfter
    );

    @Query("""
            select n
            from News n
            where n.id <> :newsId
              and coalesce(n.isKapDisclosure, false) = false
              and n.publishedAt >= :publishedAfter
              and lower(n.category) = lower(:category)
            order by n.publishedAt desc, n.createdAt desc
            """)
    List<News> findRecentCandidatesForRelatedNewsByCategory(
            @Param("newsId") Long newsId,
            @Param("category") String category,
            @Param("publishedAfter") LocalDateTime publishedAfter
    );

    @Query("""
            select n
            from News n
            where lower(n.source) in :sources
              and lower(n.title) in :titles
              and n.publishedAt >= :publishedAfter
            """)
    List<News> findRecentPotentialDuplicates(
            @Param("sources") Collection<String> sources,
            @Param("titles") Collection<String> titles,
            @Param("publishedAfter") LocalDateTime publishedAfter
    );

    @Query("""
            select n
            from News n
            where coalesce(n.isKapDisclosure, false) = false
            order by n.publishedAt desc, n.createdAt desc
            """)
    List<News> findRecentNormalNews(Pageable pageable);

    @Modifying
    long deleteByProvider(String provider);
}



