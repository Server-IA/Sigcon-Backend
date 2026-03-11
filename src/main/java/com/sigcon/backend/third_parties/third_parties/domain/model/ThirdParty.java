package com.sigcon.backend.third_parties.third_parties.domain.model;

import com.sigcon.backend.parametrization.parameters.domain.model.Municipality;
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
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ThirdParty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

