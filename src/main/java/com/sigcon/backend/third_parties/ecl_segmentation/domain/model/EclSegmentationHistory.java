package com.sigcon.backend.third_parties.ecl_segmentation.domain.model;

import java.time.LocalDateTime;

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
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "risk_segmentation_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EclSegmentationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; 
    @Column(name = "client_id", nullable = false)
    @NotNull(message = "El cliente es obligatorio")
    private Long clientId;
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

    @PrePersist
    protected void onCreate() {
        this.changeDate = LocalDateTime.now();
    }
}
