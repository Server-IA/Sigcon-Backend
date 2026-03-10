package com.sigcon.backend.third_parties.domain.model;

import com.sigcon.backend.parametrization.parameters.domain.model.Municipality;
import com.sigcon.backend.third_parties.domain.model.enums.PersonType;
import com.sigcon.backend.third_parties.domain.model.enums.TaxRegime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "person_type", nullable = false, length = 16)
    private PersonType personType;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "third_party_role_assignments",
            joinColumns = @JoinColumn(name = "third_party_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<ThirdPartyRoleCatalog> roles;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "status_id", nullable = false)
    private ThirdPartyStatusCatalog status;

    @Column(name = "blocking_reason", length = 500)
    private String blockingReason;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "municipality_id")
    private Municipality municipality;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "email", length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_regime", length = 32)
    private TaxRegime taxRegime;

    @Column(name = "fiscal_responsibilities", length = 255)
    private String fiscalResponsibilities;

    @Column(name = "withholding_info", length = 255)
    private String withholdingInfo;

    @Column(name = "credit_limit", precision = 19, scale = 2)
    private BigDecimal creditLimit;

    @Column(name = "payment_terms", length = 100)
    private String paymentTerms;

    @Column(name = "market_segment", length = 100)
    private String marketSegment;

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
