package com.emrehalli.financeportal.portfolio.entity;

import com.emrehalli.financeportal.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "portfolios")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "portfolio_name", nullable = false)
    private String portfolioName;

    @Convert(converter = PortfolioVisibilityConverter.class)
    @Column(name = "visibility_status", nullable = false, length = 30)
    private PortfolioVisibility visibilityStatus;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // En önemli kısım: User ile ilişki
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}