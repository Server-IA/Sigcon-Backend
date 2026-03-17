package com.sigcon.backend.assets.niif_alerts.domain.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "niif_parameters")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NiifParameter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_category", nullable = false)
    private String assetCategory;

    @Column(name = "depreciation_method", nullable = false)
    private String depreciationMethod;

    @Column(name = "standard_useful_life", nullable = false)
    private Integer standardUsefulLife;

    @Column(name = "revaluation_months_limit")
    private Integer revaluationMonthsLimit;

    @Column(name = "requires_impairment")
    private Boolean requiresImpairment;

}