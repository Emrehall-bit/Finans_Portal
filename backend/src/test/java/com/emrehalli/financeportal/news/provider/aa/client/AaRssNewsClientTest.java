package com.emrehalli.financeportal.news.provider.aa.client;

import com.emrehalli.financeportal.news.dto.response.NewsItemDto;
import com.emrehalli.financeportal.news.provider.aa.AaNewsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AaRssNewsClientTest {

    private final AaRssNewsClient client = new AaRssNewsClient(new RestTemplate(), properties());

    @Test
    void parsesRssItemsIntoNewsDtos() {
        List<NewsItemDto> items = client.parse("""
                <rss version="2.0">
                  <channel>
                    <title>AA Ekonomi</title>
                    <item>
                      <title>TCMB faiz kararini acikladi</title>
                      <link>https://www.aa.com.tr/tr/ekonomi/tcmb-faiz-karari/123</link>
                      <guid>aa-guid-123</guid>
                      <description>Merkez Bankasi yeni karari duyurdu.</description>
                      <pubDate>Sat, 25 Apr 2026 10:30:00 GMT</pubDate>
                    </item>
                  </channel>
                </rss>
                """);

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.getExternalId()).isEqualTo("aa-guid-123");
            assertThat(item.getTitle()).isEqualTo("TCMB faiz kararini acikladi");
            assertThat(item.getUrl()).isEqualTo("https://www.aa.com.tr/tr/ekonomi/tcmb-faiz-karari/123");
            assertThat(item.getSummary()).isEqualTo("Merkez Bankasi yeni karari duyurdu.");
            assertThat(item.getSource()).isEqualTo("Anadolu Ajansı");
            assertThat(item.getProvider()).isEqualTo("AA_RSS");
            assertThat(item.getCategory()).isEqualTo("ECONOMY");
            assertThat(item.getLanguage()).isEqualTo("tr");
            assertThat(item.getRegionScope()).isEqualTo("TR");
            assertThat(item.getPublishedAt()).isNotNull();
        });
    }

    @Test
    void preservesTurkishCharactersWhenDecodedAsUtf8() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <item>
                      <title>Bakan Bolat: Transit ticaret ve yurt dışı alım-satım kazançlarındaki vergi indirimini yüzde 100'e çıkarıyoruz</title>
                      <link>https://www.aa.com.tr/tr/ekonomi/vergi-indirimi/123</link>
                      <guid>aa-guid-tr-123</guid>
                      <description>Yurt dışı alım-satım ve kazançlarındaki düzenleme yüzde olarak artırılıyor.</description>
                      <pubDate>Sat, 25 Apr 2026 10:30:00 GMT</pubDate>
                    </item>
                  </channel>
                </rss>
                """;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("application", "rss+xml", StandardCharsets.UTF_8));
        ResponseEntity<byte[]> response = new ResponseEntity<>(xml.getBytes(StandardCharsets.UTF_8), headers, HttpStatus.OK);

        String decoded = client.decodeResponseBody(response);
        List<NewsItemDto> items = client.parse(decoded);

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.getTitle()).contains("dışı", "alım-satım", "kazançlarındaki", "yüzde", "çıkarıyoruz");
            assertThat(item.getSummary()).contains("dışı", "alım-satım");
        });
    }

    @Test
    void usesXmlDeclarationCharsetWhenContentTypeCharsetIsMissing() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <item>
                      <title>Ekonomi büyümesi üçüncü çeyrekte hızlandı</title>
                      <link>https://www.aa.com.tr/tr/ekonomi/buyume/456</link>
                      <guid>aa-guid-tr-456</guid>
                      <description>Şirketlerin kârlılığı ve ihracatı güçlendi.</description>
                    </item>
                  </channel>
                </rss>
                """;
        ResponseEntity<byte[]> response = new ResponseEntity<>(xml.getBytes(StandardCharsets.UTF_8), new HttpHeaders(), HttpStatus.OK);

        String decoded = client.decodeResponseBody(response);
        List<NewsItemDto> items = client.parse(decoded);

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.getTitle()).contains("üçüncü", "çeyrekte");
            assertThat(item.getSummary()).contains("Şirketlerin", "kârlılığı", "güçlendi");
        });
    }

    @Test
    void usesLinkHashWhenGuidIsMissing() {
        List<NewsItemDto> items = client.parse("""
                <rss version="2.0">
                  <channel>
                    <item>
                      <title>Borsa gunu yukselisle tamamladi</title>
                      <link>https://www.aa.com.tr/tr/ekonomi/borsa-gunu/456</link>
                      <description>Ozet</description>
                      <pubDate>Sat, 25 Apr 2026 10:30:00 GMT</pubDate>
                    </item>
                  </channel>
                </rss>
                """);

        assertThat(items).singleElement().satisfies(item ->
                assertThat(item.getExternalId()).startsWith("AA_RSS-")
        );
    }

    @Test
    void leavesPublishedAtNullWhenPubDateCannotBeParsed() {
        List<NewsItemDto> items = client.parse("""
                <rss version="2.0">
                  <channel>
                    <item>
                      <title>Enerji piyasalarinda hareketlilik suruyor</title>
                      <link>https://www.aa.com.tr/tr/ekonomi/enerji-piyasalari/789</link>
                      <guid>aa-guid-789</guid>
                      <description>Ozet</description>
                      <pubDate>gecersiz-tarih</pubDate>
                    </item>
                  </channel>
                </rss>
                """);

        assertThat(items).singleElement().satisfies(item ->
                assertThat(item.getPublishedAt()).isNull()
        );
    }

    @Test
    void fallsBackToHtmlArticleLinksWhenFeedIsHtml() {
        List<NewsItemDto> items = client.parse("""
                <html>
                  <body>
                    <article>
                      <a href="https://www.aa.com.tr/tr/ekonomi/ihracat-verileri/999">
                        Ihracat verileri guclu seyrini korudu
                      </a>
                    </article>
                  </body>
                </html>
                """);

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.getExternalId()).startsWith("AA_RSS-");
            assertThat(item.getTitle()).isEqualTo("Ihracat verileri guclu seyrini korudu");
            assertThat(item.getUrl()).isEqualTo("https://www.aa.com.tr/tr/ekonomi/ihracat-verileri/999");
            assertThat(item.getSource()).isEqualTo("Anadolu Ajansı");
            assertThat(item.getPublishedAt()).isNull();
        });
    }

    private AaNewsProperties properties() {
        AaNewsProperties properties = new AaNewsProperties();
        properties.setEnabled(true);
        properties.setRssUrl("https://www.aa.com.tr/tr/rss/default?cat=ekonomi");
        properties.setDefaultCategory("ECONOMY");
        properties.setDefaultLanguage("tr");
        properties.setDefaultRegionScope("TR");
        return properties;
    }
}
