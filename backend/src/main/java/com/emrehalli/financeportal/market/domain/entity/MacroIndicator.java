package com.emrehalli.financeportal.market.domain.entity;

import com.emrehalli.financeportal.market.domain.enums.MacroFrequency;
import com.emrehalli.financeportal.market.domain.enums.MacroSourceName;
import com.emrehalli.financeportal.market.domain.enums.MacroUnit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "market_macro_indicators",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_macro_indicator_code", columnNames = "code")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MacroIndicator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private MacroSourceName source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MacroFrequency frequency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MacroUnit unit;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}



