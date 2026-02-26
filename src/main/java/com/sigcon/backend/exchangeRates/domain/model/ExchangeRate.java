package com.sigcon.backend.exchangeRates.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "exchange_rates")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long currencyId;

    private String currencyIso;

    @Enumerated(EnumType.STRING)
    private ExchangeType exchangeType;

    private Double value;

    private LocalDate startDate;

    private LocalDate endDate;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;
}