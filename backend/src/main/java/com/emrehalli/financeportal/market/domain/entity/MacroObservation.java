package com.emrehalli.financeportal.market.domain.entity;

import com.emrehalli.financeportal.market.domain.enums.MacroSourceName;
import com.emrehalli.financeportal.market.domain.enums.MacroValueType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "market_macro_observations",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_macro_observation",
                        columnNames = {"indicator_id", "observation_date", "value_type"}
                )
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MacroObservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "indicator_id", nullable = false)
    private MacroIndicator indicator;

    @Column(name = "observation_date", nullable = false)
    private LocalDate observationDate;

    @Column(name = "period_label", nullable = false, length = 20)
    private String periodLabel;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal value;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 30)
    private MacroValueType valueType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private MacroSourceName source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
