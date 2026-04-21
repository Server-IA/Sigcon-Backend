package com.sigcon.backend.third_parties.third_parties.domain.model;

import com.sigcon.backend.parametrization.resources.domain.model.Municipality;
import com.sigcon.backend.parametrization.resources.domain.model.TypeOrganization;
import com.sigcon.backend.parametrization.resources.domain.model.TypeRegimen;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "third_parties")
@SQLDelete(sql = "UPDATE third_parties SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@Filter(name = "tenantFilter", condition = "company_id = :tenantId")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ThirdParty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Multi-tenant (V10-A, 2026-04-19): company_id NOT NULL en BD.
     * Se auto-inyecta en @PrePersist desde TenantContext. El listado filtra
     * automaticamente por tenant via @Filter.
     */
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "third_party_code", nullable = false, length = 32)
    private String thirdPartyCode;

    @Column(name = "nit", nullable = false, length = 15)
    private String nit;

    @Column(name = "dv", nullable = false, length = 2)
    private String dv;

    @Column(name = "business_name", nullable = false, length = 255)
    private String businessName;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "third_party_role_assignments", joinColumns = @JoinColumn(name = "third_party_id"), inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<ThirdPartyRoleCatalog> roles;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "status_id", nullable = false)
    private ThirdPartyStatusCatalog status;

    @Column(name = "blocking_reason", length = 500)
    private String blockingReason;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "municipality_id")
    private Municipality municipality;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "type_organization_id")
    private TypeOrganization typeOrganization;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "type_regimen_id")
    private TypeRegimen typeRegimen;

    @Column(name = "credit_limit", precision = 19, scale = 2)
    private BigDecimal creditLimit;

    @Column(name = "payment_terms", length = 100)
    private String paymentTerms;

    @Column(name = "market_segment", length = 100)
    private String marketSegment;

    @OneToMany(mappedBy = "thirdParty", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<ThirdContact> contacts = new ArrayList<>();

    @OneToMany(mappedBy = "thirdParty", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<ThirdPartyWithholdingAssignment> withholdingAssignments = new ArrayList<>();

    /** TER-04: Asignaciones de roles con vigencia temporal */
    @OneToMany(mappedBy = "thirdParty", fetch = FetchType.LAZY)
    private List<ThirdPartyRoleAssignment> roleAssignments;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_reason", length = 500)
    private String deletedReason;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        // Multi-tenant (V10-A): si no viene el companyId del caller, leerlo del
        // TenantContext. Evita que el frontend deba setearlo explicitamente.
        // PLATFORM_ADMIN no puede crear terceros (no tiene tenant) — la UI
        // no lo deja y este listener tampoco inyecta.
        if (this.companyId == null) {
            this.companyId = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
        }
    }

    /**
     * Guarda contra acceso cross-tenant por PK (findById, getReference, etc.)
     * que NO son filtrados por {@code @Filter} de Hibernate. Si el tenant
     * actual es distinto al de la entidad Y no es PLATFORM_ADMIN, se lanza
     * {@link com.sigcon.backend.platform.tenant.TenantIsolationException}
     * que el GlobalExceptionHandler traduce a HTTP 404.
     */
    @jakarta.persistence.PostLoad
    protected void onLoad() {
        if (com.sigcon.backend.platform.tenant.TenantContext.isPlatformAdmin()) return;
        Long current = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
        if (current == null || this.companyId == null) return;
        if (!current.equals(this.companyId)) {
            throw new com.sigcon.backend.platform.tenant.TenantIsolationException(
                    "Recurso fuera del tenant actual");
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
