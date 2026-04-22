package com.emrehalli.financeportal.market.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketEventDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private String source;
    private String eventType;
    private String title;
    private String symbol;
    private String issuerCode;
    private LocalDateTime publishedAt;
    private String url;
    private String summary;
    private String rawPayload;
    private LocalDateTime fetchedAt;
}
