package com.sigcon.backend.assets.niif_alerts.domain.model;

import com.sigcon.backend.assets.assets.domain.model.Assets;
import com.sigcon.backend.assets.niif_alerts.domain.model.enums.NiifCorrectionType;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

@Entity
@Table(name = "niif_corrections")
@SQLDelete(sql = "UPDATE niif_corrections SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NiifCorrection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Assets asset;

    @Enumerated(EnumType.STRING)
    private NiifCorrectionType correctionType;

    private Integer newUsefulLifeMonths;

    private BigDecimal newBookValue;

    private String observations;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate(){
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

}