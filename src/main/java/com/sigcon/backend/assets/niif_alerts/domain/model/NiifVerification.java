package com.sigcon.backend.assets.niif_alerts.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "niif_verifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NiifVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(name = "result", nullable = false)
    private String result;

    @Column(name = "message")
    private String message;

    @Column(name = "verification_date")
    private LocalDateTime verificationDate;

}