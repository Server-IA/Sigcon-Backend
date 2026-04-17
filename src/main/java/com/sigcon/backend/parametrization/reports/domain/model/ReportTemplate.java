package com.sigcon.backend.parametrization.reports.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.Where;

import jakarta.persistence.*;
import jakarta.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entidad JPA que representa una plantilla de reporte asociada a un tipo de reporte.
 * Implementa soft delete mediante el campo deleted_at.
 * Cada plantilla tiene un versionamiento automatico por tipo de reporte.
 */
@Entity
@Table(name = "report_templates")
@SQLDelete(sql = "UPDATE report_templates SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReportTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_type_id", nullable = false)
    private ReportType reportType;

    @Builder.Default
    @Column(nullable = false)
    private Integer version = 1;

    @Column(name = "file_path", length = 500)
    @Nullable
    private String filePath;

    @Column(name = "file_name", length = 255)
    @Nullable
    private String fileName;

    @Column(name = "mime_type", length = 100)
    @Nullable
    private String mimeType;

    @Column(name = "file_size")
    @Nullable
    private Long fileSize;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "file_content")
    @Nullable
    private byte[] fileContent;

    @Column(length = 500)
    @Nullable
    private String description;

    /** HU-PA-RF-39 E1: fecha inicio vigencia (obligatoria). */
    @Column(name = "valid_from")
    @Nullable
    private LocalDate validFrom;

    /** HU-PA-RF-39 E1: fecha fin vigencia (opcional, NULL = indefinido). */
    @Column(name = "valid_to")
    @Nullable
    private LocalDate validTo;

    /** HU-PA-RF-39 E3: marca la plantilla por defecto del tipo de reporte. */
    @Builder.Default
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

    @Builder.Default
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_at")
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
