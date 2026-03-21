package com.sigcon.backend.assets.niif_alerts.domain.model;

import com.sigcon.backend.assets.niif_alerts.domain.model.enums.NiifSeverity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

@Entity
@Table(name = "niif_alerts")
@SQLDelete(sql = "UPDATE niif_alerts SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NiifAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verification_id", nullable = false)
    private NiifVerification verification;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NiifSeverity severity;

    @Column(nullable = false, length = 500)
    private String message;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate(){
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

}