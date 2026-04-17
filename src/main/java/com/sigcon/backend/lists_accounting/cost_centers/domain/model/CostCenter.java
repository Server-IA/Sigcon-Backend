package com.sigcon.backend.lists_accounting.cost_centers.domain.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.Where;

import com.sigcon.backend.lists_accounting.cost_centers.domain.model.enums.CostCenterStatus;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "cost_centers")
@SQLDelete(sql = "UPDATE cost_centers SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CostCenter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El código del centro de costo es obligatorio")
    @Column(nullable = false)
    private String code;

    @NotBlank(message = "El nombre del centro de costo es obligatorio")
    @Column(nullable = false)
    private String name;

    @Nullable
    private String description;

    @NotNull(message = "El estado es obligatorio")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private CostCenterStatus status = CostCenterStatus.ACTIVE;

    @Column(name = "created_at", updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @Temporal(TemporalType.TIMESTAMP)
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    @Nullable
    private LocalDateTime deletedAt;

    @Column(name = "deletion_reason")
    @Nullable
    private String deletionReason;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
