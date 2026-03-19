package com.sigcon.backend.bank.banks.domain.model;
import com.sigcon.backend.bank.banks.domain.model.enums.BankStatus;
import com.sigcon.backend.bank.banks.domain.model.enums.BankType;
import com.sigcon.backend.bank.banks.domain.model.enums.FormatExtract;
import com.sigcon.backend.parametrization.resources.domain.model.Country;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.List;
@Entity
@Table(name = "banks")
@SQLDelete(sql = "UPDATE banks SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Bank {

     @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 45)
    @NotNull(message = "El código del banco no puede ser nulo")
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    @NotBlank(message = "El nombre del banco no puede estar vacío")
    private String name;

    @Column(name = "name_short", nullable = false, length = 45)
    @NotBlank(message = "El nombre corto del banco no puede estar vacío")
    private String nameShort;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_bank", nullable = false)
    @NotBlank(message = "El tipo del banco no puede estar vacío")
    private BankType typeBank;

    @Column(name = "nit", nullable = false, length = 45)
    @NotBlank(message = "El NIT del banco no puede estar vacío")
    private String nit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "country_id", nullable = false)
    @NotNull(message = "El país del banco no puede ser nulo")
    private Country country;

    @Column(name = "swift", nullable = false, length = 30)
    @NotBlank(message = "El código SWIFT del banco no puede estar vacío")
    private String swift;

    @Column(name = "code_ach", length = 45)
    @NotBlank(message = "El código ACH del banco no puede estar vacío")
    private String codeAch;

    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    @JoinColumn(name = "bank_id")
    private List<BankBranch> branches;

    @Column(name = "url_webservice", length = 45)
    private String urlWebservice;

    @Column(name = "conciliation_days")
    private Integer conciliationDays;

    @Column(name = "phone", length = 12)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private BankStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "format_extract")
    private FormatExtract formatExtract;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.status = BankStatus.ACTIVE;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

