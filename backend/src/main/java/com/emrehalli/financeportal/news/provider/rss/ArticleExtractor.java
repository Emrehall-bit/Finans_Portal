package com.emrehalli.financeportal.news.provider.rss;

import com.emrehalli.financeportal.news.enums.NewsProviderType;
import org.jsoup.nodes.Document;

public interface ArticleExtractor {

    boolean supports(NewsProviderType providerType);

    ArticleExtractionResult extract(Document document, String baseUrl);
}



