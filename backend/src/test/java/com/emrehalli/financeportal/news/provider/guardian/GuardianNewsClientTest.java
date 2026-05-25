package com.emrehalli.financeportal.news.provider.guardian;

import com.emrehalli.financeportal.news.enums.NewsQualityStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class GuardianNewsClientTest {

    private final GuardianNewsProperties properties = new GuardianNewsProperties();
    private final GuardianNewsClient client = new GuardianNewsClient(new RestTemplate(), properties, new ObjectMapper());

    @Test
    void mapsBodyTextAndImageAsFullContent() throws Exception {
        String payload = """
                {
                  "response": {
                    "results": [
                      {
                        "id": "business/2026/may/22/fed-signals-rate-path",
                        "webTitle": "Fed signals a slower rate path",
                        "webUrl": "https://www.theguardian.com/business/2026/may/22/fed-signals-rate-path",
                        "webPublicationDate": "2026-05-22T09:30:00Z",
                        "fields": {
                          "thumbnail": "https://media.guim.co.uk/image.jpg",
                          "trailText": "<p>Markets are repricing rates.</p>",
                          "bodyText": "The Fed signaled a slower rate path as bond yields and the dollar moved.",
                          "shortUrl": "https://www.theguardian.com/p/short"
                        }
                      }
                    ]
                  }
                }
                """;

        var items = client.parse(payload);

        assertThat(items).singleElement()
                .satisfies(item -> {
                    assertThat(item.getTitle()).isEqualTo("Fed signals a slower rate path");
                    assertThat(item.getUrl()).isEqualTo("https://www.theguardian.com/business/2026/may/22/fed-signals-rate-path");
                    assertThat(item.getImageUrl()).isEqualTo("https://media.guim.co.uk/image.jpg");
                    assertThat(item.getSummary()).contains("bond yields and the dollar moved");
                    assertThat(item.getQualityStatus()).isEqualTo(NewsQualityStatus.FULL_CONTENT.name());
                });
    }

    @Test
    void fallsBackToTrailTextWhenBodyTextMissing() throws Exception {
        String payload = """
                {
                  "response": {
                    "results": [
                      {
                        "id": "business/2026/may/22/sterling-moves",
                        "webTitle": "Sterling moves against the dollar",
                        "webUrl": "https://www.theguardian.com/business/2026/may/22/sterling-moves",
                        "webPublicationDate": "2026-05-22T10:15:00Z",
                        "fields": {
                          "trailText": "<p>Sterling moved as traders watched rate expectations.</p>",
                          "shortUrl": "https://www.theguardian.com/p/sterling"
                        }
                      }
                    ]
                  }
                }
                """;

        var items = client.parse(payload);

        assertThat(items).singleElement()
                .satisfies(item -> {
                    assertThat(item.getSummary()).isEqualTo("Sterling moved as traders watched rate expectations.");
                    assertThat(item.getQualityStatus()).isEqualTo(NewsQualityStatus.SUMMARY_ONLY.name());
                    assertThat(item.getImageUrl()).isNull();
                });
    }
}



