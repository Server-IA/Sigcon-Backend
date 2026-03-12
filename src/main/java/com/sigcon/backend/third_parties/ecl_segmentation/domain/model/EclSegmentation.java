package com.sigcon.backend.third_parties.ecl_segmentation.domain.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import com.sigcon.backend.third_parties.ecl_segmentation.domain.model.enums.RiskSegmentation;
import com.sigcon.backend.third_parties.ecl_segmentation.domain.model.enums.SegmentationSource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "risk_segmentation")
@SQLDelete(sql = "UPDATE risk_segmentation SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EclSegmentation { 

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "client_id", nullable = false, unique = true)
    @NotNull(message = "El cliente es obligatorio")
    private Long clientId;
    @Enumerated(EnumType.STRING)
    @Column(name = "auto_segment", nullable = false)
    @NotNull(message = "El segmento de riesgo automatico es obligatorio")
    private RiskSegmentation autoSegment;
    @Enumerated(EnumType.STRING)
    @Column(name = "final_segment", nullable = false)
    @NotNull(message = "El segmento de riesgo final es obligatorio")
    private RiskSegmentation finalSegment;
    @Enumerated(EnumType.STRING)
    @Column(name = "segmentation_source", nullable = false)
    @Builder.Default
    private SegmentationSource segmentationSource = SegmentationSource.AUTOMATIC; 
    @Column(name = "justification", columnDefinition = "TEXT")
    private String justification;
    @Column(name = "calculation_date", nullable = false)
    @NotNull(message = "La fecha de cálculo es obligatoria")
    private LocalDateTime calculationDate;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    } 

    @PreRemove
    protected void onDelete() {
        this.deletedAt = LocalDateTime.now();
    }

}
