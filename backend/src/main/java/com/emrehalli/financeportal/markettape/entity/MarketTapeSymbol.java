package com.emrehalli.financeportal.markettape.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "market_tape_symbols")
@Getter
@Setter
@NoArgsConstructor
public class MarketTapeSymbol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "symbol", nullable = false, length = 40, unique = true)
    private String symbol;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;
}
