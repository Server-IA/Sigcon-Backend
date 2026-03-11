package com.sigcon.backend.third_parties.commercial_data.domain.model;

import org.hibernate.annotations.Where;
import org.hibernate.annotations.SQLDelete;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.sigcon.backend.parametrization.resources.domain.model.PaymentTerms;
import com.sigcon.backend.third_parties.ecl_segmentation.domain.model.enums.RiskSegmentation;
import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdParty;


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
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "commercial_data")
@SQLDelete(sql = "UPDATE commercial_data SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CommercialData {

    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private ThirdParty thirdParty; // Relación con ThirdParty
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_term_id", nullable = false)
    @NotNull(message = "El plazo de pago es obligatorio")
    private PaymentTerms paymentTerm;
    @Column(name = "limit_credit", precision = 20, scale = 2)
    private BigDecimal limitCredit;
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level")
    private RiskSegmentation riskLevel; 
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

    @PreRemove
    protected void onDelete() {
        this.deletedAt = LocalDateTime.now();
    }


}
