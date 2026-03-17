package com.sigcon.backend.parametrization.companies.domain.model;

import com.sigcon.backend.parametrization.resources.domain.model.TypeOrganization;
import com.sigcon.backend.parametrization.resources.domain.model.TypeRegimen;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

@Entity
@Table(name = "companies")
@SQLDelete(sql = "UPDATE companies SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "type_regimen_id", nullable = false)
    private TypeRegimen typeRegimen;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "type_organization_id", nullable = false)
    private TypeOrganization typeOrganization;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "nit", nullable = false, length = 15)
    private String nit;

    @Column(name = "dv", nullable = false, length = 1)
    private String dv;

    @Column(name = "legal_representative", length = 255)
    private String legalRepresentative;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "size", length = 45)
    private String size;

    @Column(name = "phone", length = 12)
    private String phone;

    @Column(name = "logo", length = 255)
    private String logo;

    @Column(name = "status", length = 45, nullable = false)
    private String status;

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
}

