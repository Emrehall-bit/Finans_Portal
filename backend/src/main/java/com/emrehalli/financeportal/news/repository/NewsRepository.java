package com.emrehalli.financeportal.news.repository;

import com.emrehalli.financeportal.news.entity.News;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NewsRepository extends JpaRepository<News, Long> {

    List<News> findAllByOrderByPublishedAtDesc();

    boolean existsByExternalId(String externalId);

    List<News> findAllByCategoryIgnoreCaseOrderByPublishedAtDesc(String category);

    List<News> findAllByRelatedSymbolIgnoreCaseOrderByPublishedAtDesc(String relatedSymbol);

    List<News> findAllByTitleContainingIgnoreCaseOrSummaryContainingIgnoreCaseOrderByPublishedAtDesc(String titleKeyword, String summaryKeyword);

    List<News> findAllByProviderOrderByPublishedAtDesc(String provider);

    List<News> findAllByRegionScopeOrderByPublishedAtDesc(String regionScope);

    List<News> findAllByProviderAndCategoryIgnoreCaseOrderByPublishedAtDesc(String provider, String category);

    List<News> findAllByRegionScopeAndCategoryIgnoreCaseOrderByPublishedAtDesc(String regionScope, String category);

    List<News> findAllByProviderAndRelatedSymbolIgnoreCaseOrderByPublishedAtDesc(String provider, String relatedSymbol);

    List<News> findAllByRegionScopeAndRelatedSymbolIgnoreCaseOrderByPublishedAtDesc(String regionScope, String relatedSymbol);

    List<News> findAllByProviderAndTitleContainingIgnoreCaseOrProviderAndSummaryContainingIgnoreCaseOrderByPublishedAtDesc(
            String providerTitle, String titleKeyword, String providerSummary, String summaryKeyword);

    List<News> findAllByRegionScopeAndTitleContainingIgnoreCaseOrRegionScopeAndSummaryContainingIgnoreCaseOrderByPublishedAtDesc(
            String scopeTitle, String titleKeyword, String scopeSummary, String summaryKeyword);
}
