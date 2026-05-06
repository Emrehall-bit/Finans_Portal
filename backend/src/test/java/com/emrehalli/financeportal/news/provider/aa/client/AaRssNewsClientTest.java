package com.emrehalli.financeportal.news.provider.aa.client;

import com.emrehalli.financeportal.news.dto.response.NewsItemDto;
import com.emrehalli.financeportal.news.provider.aa.AaNewsProperties;
import com.emrehalli.financeportal.news.provider.rss.RssFeedSupport;
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

    private final AaRssNewsClient client = new AaRssNewsClient(new RestTemplate(), properties(), new RssFeedSupport());

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
            assertThat(item.getSource()).isEqualTo("Anadolu Ajans\u0131");
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
                      <title>Bakan Bolat: Transit ticaret ve yurt d\u0131\u015f\u0131 al\u0131m-sat\u0131m kazan\u00e7lar\u0131ndaki vergi indirimini y\u00fczde 100'e \u00e7\u0131kar\u0131yoruz</title>
                      <link>https://www.aa.com.tr/tr/ekonomi/vergi-indirimi/123</link>
                      <guid>aa-guid-tr-123</guid>
                      <description>Yurt d\u0131\u015f\u0131 al\u0131m-sat\u0131m ve kazan\u00e7lar\u0131ndaki d\u00fczenleme y\u00fczde olarak art\u0131r\u0131l\u0131yor.</description>
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
            assertThat(item.getTitle()).contains("d\u0131\u015f\u0131", "al\u0131m-sat\u0131m", "kazan\u00e7lar\u0131ndaki", "y\u00fczde", "\u00e7\u0131kar\u0131yoruz");
            assertThat(item.getSummary()).contains("d\u0131\u015f\u0131", "al\u0131m-sat\u0131m", "d\u00fczenleme");
        });
    }

    @Test
    void usesXmlDeclarationCharsetWhenContentTypeCharsetIsMissing() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <item>
                      <title>Ekonomi b\u00fcy\u00fcmesi \u00fc\u00e7\u00fcnc\u00fc \u00e7eyrekte h\u0131zland\u0131</title>
                      <link>https://www.aa.com.tr/tr/ekonomi/buyume/456</link>
                      <guid>aa-guid-tr-456</guid>
                      <description>\u015eirketlerin k\u00e2rl\u0131l\u0131\u011f\u0131 ve ihracat\u0131 g\u00fc\u00e7lendi.</description>
                    </item>
                  </channel>
                </rss>
                """;
        ResponseEntity<byte[]> response = new ResponseEntity<>(xml.getBytes(StandardCharsets.UTF_8), new HttpHeaders(), HttpStatus.OK);

        String decoded = client.decodeResponseBody(response);
        List<NewsItemDto> items = client.parse(decoded);

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.getTitle()).contains("\u00fc\u00e7\u00fcnc\u00fc", "\u00e7eyrekte");
            assertThat(item.getSummary()).contains("\u015eirketlerin", "k\u00e2rl\u0131l\u0131\u011f\u0131", "g\u00fc\u00e7lendi");
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
    void prefersMediaContentImageUrlWhenPresent() {
        List<NewsItemDto> items = client.parse("""
                <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
                  <channel>
                    <item>
                      <title>Merkez Bankasi karari</title>
                      <link>https://www.aa.com.tr/tr/ekonomi/karar/123</link>
                      <guid>aa-image-1</guid>
                      <media:content url="https://cdn.example.com/media.jpg" type="image/jpeg" />
                      <enclosure url="https://cdn.example.com/enclosure.jpg" type="image/jpeg" />
                      <description><![CDATA[<img src="https://cdn.example.com/description.jpg" />Ozet]]></description>
                    </item>
                  </channel>
                </rss>
                """);

        assertThat(items).singleElement().satisfies(item ->
                assertThat(item.getImageUrl()).isEqualTo("https://cdn.example.com/media.jpg")
        );
    }

    @Test
    void fallsBackToEnclosureImageUrlWhenMediaContentIsMissing() {
        List<NewsItemDto> items = client.parse("""
                <rss version="2.0">
                  <channel>
                    <item>
                      <title>Ihracat verileri</title>
                      <link>https://www.aa.com.tr/tr/ekonomi/ihracat/124</link>
                      <guid>aa-image-2</guid>
                      <enclosure url="https://cdn.example.com/enclosure.jpg" type="image/jpeg" />
                    </item>
                  </channel>
                </rss>
                """);

        assertThat(items).singleElement().satisfies(item ->
                assertThat(item.getImageUrl()).isEqualTo("https://cdn.example.com/enclosure.jpg")
        );
    }

    @Test
    void fallsBackToDescriptionImageWhenFeedImageFieldsAreMissing() {
        List<NewsItemDto> items = client.parse("""
                <rss version="2.0">
                  <channel>
                    <item>
                      <title>Kur gelismeleri</title>
                      <link>https://www.aa.com.tr/tr/ekonomi/kur/125</link>
                      <guid>aa-image-3</guid>
                      <description><![CDATA[<p><img src="https://cdn.example.com/description.jpg" /></p><p>Ozet</p>]]></description>
                    </item>
                  </channel>
                </rss>
                """);

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.getImageUrl()).isEqualTo("https://cdn.example.com/description.jpg");
            assertThat(item.getSummary()).isEqualTo("Ozet");
        });
    }

    @Test
    void enrichesImageUrlFromArticlePageWhenFeedDoesNotContainImageMetadata() {
        List<NewsItemDto> items = client.parse("""
                <rss version="2.0">
                  <channel>
                    <item>
                      <title>Sanayi uretimi</title>
                      <link>https://www.aa.com.tr/tr/ekonomi/sanayi/126</link>
                      <guid>aa-image-4</guid>
                      <description>Metin ozet</description>
                    </item>
                  </channel>
                </rss>
                """);

        assertThat(items).singleElement().satisfies(item ->
                assertThat(item.getImageUrl()).isEqualTo("https://www.aa.com.tr/images/meta-photo.png")
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
            assertThat(item.getSource()).isEqualTo("Anadolu Ajans\u0131");
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
