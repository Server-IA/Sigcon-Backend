package com.sigcon.backend.assets.domain.model;

import com.sigcon.backend.assets.domain.model.enums.AssetClassification;
import com.sigcon.backend.assets.domain.model.enums.AssetStatus;
import com.sigcon.backend.assets.domain.model.enums.AssetType;
import com.sigcon.backend.assets.domain.model.enums.DepreciationMethod;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.ChartOfAccount;
import com.sigcon.backend.third_parties.domain.model.ThirdParty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "assets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Assets {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_code", nullable = false, length = 30)
    private String assetCode;

    @Column(name = "asset_name", nullable = false, length = 150)
    private String assetName;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "classification", nullable = false, length = 20)
    private AssetClassification classification;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 20)
    private AssetType assetType;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "chart_of_account_id", nullable = false)
    private ChartOfAccount chartOfAccount;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "supplier_id", nullable = false)
    private ThirdParty supplier;

    @Column(name = "acquisition_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal acquisitionValue;

    @Column(name = "acquisition_date", nullable = false)
    private LocalDate acquisitionDate;

    @Column(name = "useful_life_months", nullable = false)
    private Integer usefulLifeMonths;

    @Enumerated(EnumType.STRING)
    @Column(name = "depreciation_method", nullable = false, length = 40)
    private DepreciationMethod depreciationMethod;

    @Column(name = "payment_terms", nullable = false, length = 120)
    private String paymentTerms;

    @Column(name = "accounts_payable_reference_id")
    private Long accountsPayableReferenceId;

    @Column(name = "bank_cash_reference_id")
    private Long bankCashReferenceId;

    @Column(name = "cost_center_or_accounting_location", length = 120)
    private String costCenterOrAccountingLocation;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_status", nullable = false, length = 30)
    private AssetStatus status;

    @Column(name = "observations", length = 500)
    private String observations;

    @Column(name = "created_by", length = 150)
    private String createdBy;

    @Column(name = "updated_by", length = 150)
    private String updatedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;

        if (this.status == null) {
            this.status = AssetStatus.ACTIVE;
        }

        if (this.createdBy == null || this.createdBy.isBlank()) {
            this.createdBy = "sistema";
        }

        if (this.updatedBy == null || this.updatedBy.isBlank()) {
            this.updatedBy = this.createdBy;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
