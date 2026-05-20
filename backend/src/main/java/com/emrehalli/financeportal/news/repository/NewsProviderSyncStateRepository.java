package com.emrehalli.financeportal.news.repository;

import com.emrehalli.financeportal.news.entity.NewsProviderSyncState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsProviderSyncStateRepository extends JpaRepository<NewsProviderSyncState, String> {
}
