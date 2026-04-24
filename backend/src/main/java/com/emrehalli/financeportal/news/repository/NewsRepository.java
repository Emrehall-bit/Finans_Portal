package com.emrehalli.financeportal.news.repository;

import com.emrehalli.financeportal.news.entity.News;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public interface NewsRepository extends JpaRepository<News, Long>, JpaSpecificationExecutor<News> {

    Set<News> findByExternalIdIn(Collection<String> externalIds);

    Optional<News> findByExternalId(String externalId);
}



