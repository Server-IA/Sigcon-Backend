package com.sigcon.backend.third_parties.ecl_segmentation.domain.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import com.sigcon.backend.third_parties.ecl_segmentation.domain.model.enums.RiskSegmentation;
import com.sigcon.backend.third_parties.ecl_segmentation.domain.model.enums.SegmentationSource;

import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdParty;

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
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "risk_segmentation_history")
@SQLDelete(sql = "UPDATE risk_segmentation_history SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EclSegmentationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; 
   @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    @NotNull(message = "El cliente es obligatorio")
    private ThirdParty client; // Relación con la entidad ThirdParty (Cliente)
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_segment", nullable = false)
    @NotNull(message = "El segmento de riesgo anterior es obligatorio")
    private RiskSegmentation previousSegment;
    @Enumerated(EnumType.STRING)
    @Column(name = "new_segment", nullable = false)
    @NotNull(message = "El nuevo segmento de riesgo es obligatorio")
    private RiskSegmentation newSegment;
    @Enumerated(EnumType.STRING)
    @Column(name = "segmentation_source", nullable = false)
    @NotNull(message = "La fuente de segmentacion es obligatoria")
    private SegmentationSource segmentationSource;
    @Column(name = "justification", columnDefinition = "TEXT")
    private String justification;
    @Column(name = "change_date", nullable = false)
    @NotNull(message = "La fecha de cambio es obligatoria")
    private LocalDateTime changeDate; 
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        this.changeDate = LocalDateTime.now();
    } 

    @PreRemove
    protected void onDelete() {
        this.deletedAt = LocalDateTime.now();
    }
}
