package com.emrehalli.financeportal.news.provider.kap;

import com.emrehalli.financeportal.news.provider.rss.RssFeedSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KapNewsClientTest {

    @Test
    void fetchCompanyNewsParsesSearchResults() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        KapNewsProperties properties = new KapNewsProperties();
        properties.setBaseUrl("https://www.kap.org.tr");
        properties.setMaxItems(10);

        server.expect(once(), requestTo("https://www.kap.org.tr/tr/search/THYAO/1"))
                .andRespond(withSuccess("""
                        <html>
                        <body>
                          <div class="notification">
                            <a href="/tr/bildirim/123456">Ozel Durum Aciklamasi THYAO</a>
                            <p>Sirket yeni filo yatirim planini acikladi.</p>
                            <span>Gonderim Tarihi</span>
                            <span>04/05/2026 10:15:00</span>
                          </div>
                        </body>
                        </html>
                        """, MediaType.TEXT_HTML));

        KapNewsClient client = new KapNewsClient(restTemplate, properties, new RssFeedSupport());

        var items = client.fetchCompanyNews("THYAO");

        server.verify();
        assertThat(items).hasSize(1);
        assertThat(items.getFirst().getProvider()).isEqualTo("KAP");
        assertThat(items.getFirst().getSource()).isEqualTo("KAP");
        assertThat(items.getFirst().getRelatedSymbol()).isEqualTo("THYAO");
        assertThat(items.getFirst().getUrl()).isEqualTo("https://www.kap.org.tr/tr/bildirim/123456");
        assertThat(items.getFirst().getPublishedAt()).isNotNull();
    }

    @Test
    void fetchLatestNewsReturnsEmptyWhenNoNotificationBlockExists() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        KapNewsProperties properties = new KapNewsProperties();
        properties.setBaseUrl("https://www.kap.org.tr");

        server.expect(once(), requestTo("https://www.kap.org.tr/tr"))
                .andRespond(withSuccess("<html><body><a href=\"/tr/about/genel-bilgi\">Genel Bilgi</a></body></html>", MediaType.TEXT_HTML));

        KapNewsClient client = new KapNewsClient(restTemplate, properties, new RssFeedSupport());

        assertThat(client.fetchLatestNews()).isEmpty();
        server.verify();
    }
}
