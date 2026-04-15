package com.emrehalli.financeportal.watchlist.entity;

import com.emrehalli.financeportal.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "watchlist",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"user_id", "instrument_code"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Watchlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "instrument_code", nullable = false)
    private String instrumentCode;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
