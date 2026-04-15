package com.emrehalli.financeportal.news.provider.bloomberght.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BloombergHtClient {

    private static final Logger logger = LogManager.getLogger(BloombergHtClient.class);

    @Value("${bloomberght.api.url}")
    private String newsPageUrl;

    public Document fetchNewsDocument() {
        try {
            logger.info("Sending Bloomberg HT request");
            return Jsoup.connect(newsPageUrl)
                    .userAgent("Mozilla/5.0")
                    .timeout(10000)
                    .get();
        } catch (Exception e) {
            logger.error("Failed to fetch Bloomberg HT news page", e);
            return null;
        }
    }
}
