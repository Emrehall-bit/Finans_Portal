package com.emrehalli.financeportal.analysis.entity;

import com.emrehalli.financeportal.common.converter.JsonMapConverter;
import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.Map;

@Entity
@Table(name = "indicator_configs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndicatorConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false)
    private MarketInstrument instrument;

    @Column(name = "indicator_type", nullable = false, length = 50)
    private String indicatorType;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "parameters", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> parameters = new java.util.HashMap<>();

    @Column(name = "is_active")
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
