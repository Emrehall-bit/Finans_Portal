package com.emrehalli.financeportal.technicalanalysis.entity;

import com.emrehalli.financeportal.common.converter.JsonListConverter;
import com.emrehalli.financeportal.common.converter.JsonMapConverter;
import com.emrehalli.financeportal.market.domain.entity.MarketInstrument;
import com.emrehalli.financeportal.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnTransformer;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "chart_drawings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChartDrawing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false)
    private MarketInstrument instrument;

    @Column(name = "timeframe", nullable = false, length = 10)
    private String timeframe;

    @Column(name = "drawing_type", nullable = false, length = 50)
    private String drawingType;

    @Convert(converter = JsonListConverter.class)
    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "points", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private List<Map<String, Object>> points = new java.util.ArrayList<>();

    @Convert(converter = JsonMapConverter.class)
    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "style", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> style = new java.util.HashMap<>();

    @Column(name = "label", length = 100)
    private String label;

    @Column(name = "is_alert_linked")
    @Builder.Default
    private boolean isAlertLinked = false;

    @Column(name = "linked_alert_id")
    private Long linkedAlertId;

    @Column(name = "is_premium_feature")
    @Builder.Default
    private boolean isPremiumFeature = false;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}

