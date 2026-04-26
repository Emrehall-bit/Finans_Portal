package com.emrehalli.financeportal.news.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "news",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_news_external_id", columnNames = "external_id")
        },
        indexes = {
                @Index(name = "idx_news_published_at", columnList = "published_at"),
                @Index(name = "idx_news_importance_score", columnList = "importance_score"),
                @Index(name = "idx_news_provider", columnList = "provider"),
                @Index(name = "idx_news_region_scope", columnList = "region_scope"),
                @Index(name = "idx_news_category", columnList = "category"),
                @Index(name = "idx_news_related_symbol", columnList = "related_symbol")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class News {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, unique = true, length = 200)
    private String externalId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(nullable = false, length = 100)
    private String source;

    @Column(nullable = false, length = 100)
    private String provider;

    @Column(length = 10)
    private String language;

    @Column(name = "region_scope", nullable = false, length = 20)
    private String regionScope;

    @Column(length = 100)
    private String category;

    @Column(name = "related_symbol", length = 30)
    private String relatedSymbol;

    @Column(nullable = false, length = 1200)
    private String url;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Builder.Default
    @Column(name = "importance_score", nullable = false)
    private Integer importanceScore = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}


