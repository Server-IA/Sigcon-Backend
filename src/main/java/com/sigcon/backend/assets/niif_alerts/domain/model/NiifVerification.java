package com.sigcon.backend.assets.niif_alerts.domain.model;

import com.sigcon.backend.assets.assets.domain.model.Assets;
import com.sigcon.backend.assets.niif_alerts.domain.model.enums.NiifResult;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

@Entity
@Table(name = "niif_verifications")
@SQLDelete(sql = "UPDATE niif_verifications SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NiifVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Assets asset;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NiifResult result;

    @Column(length = 500)
    private String summary;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate(){
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate(){
        this.updatedAt = LocalDateTime.now();
    }

}