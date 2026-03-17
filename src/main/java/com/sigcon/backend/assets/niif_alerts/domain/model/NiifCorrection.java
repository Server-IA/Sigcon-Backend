package com.sigcon.backend.assets.niif_alerts.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "niif_corrections")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NiifCorrection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(name = "correction_type")
    private String correctionType;

    @Column(name = "justification")
    private String justification;

    @Column(name = "previous_value")
    private String previousValue;

    @Column(name = "new_value")
    private String newValue;

    @Column(name = "correction_date")
    private LocalDateTime correctionDate;

}