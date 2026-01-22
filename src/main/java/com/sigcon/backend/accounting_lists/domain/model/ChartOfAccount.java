package com.sigcon.backend.accounting_lists.domain.model;

import com.sigcon.backend.accounting_lists.domain.model.enums.AccountClass;
import com.sigcon.backend.accounting_lists.domain.model.enums.AccountLevel;
import com.sigcon.backend.accounting_lists.domain.model.enums.AccountNature;
import com.sigcon.backend.accounting_lists.domain.model.enums.AccountStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "cfg_chart_of_accounts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_puc_code", columnNames = "code"),
                @UniqueConstraint(name = "uk_puc_name", columnNames = "name")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChartOfAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    @NotBlank(message = "El código oficial de la cuenta es obligatorio")
    @Size(min = 1, max = 10, message = "El código oficial debe tener entre 1 y 10 caracteres")
    private String code; //(e.g. 1105)

    @Column(nullable = false, length = 100)
    @NotBlank(message = "El nombre de la cuenta es obligatorio")
    @Size(min = 1, max = 100, message = "El nombre de la cuenta debe tener máximo 100 caracteres")
    private String name; //(e.g. Cash)

    @Enumerated(EnumType.STRING)
    @NotNull(message = "La clase de la cuenta es obligatoria")
    @Column(nullable = false, length = 30)
    private AccountClass accountClass;

    @Enumerated(EnumType.STRING)
    @NotNull(message = "El nivel jerárquico de la cuenta es obligatorio")
    @Column(nullable = false, length = 20)
    private AccountLevel accountLevel;

    @Enumerated(EnumType.STRING)
    @NotNull(message = "La naturaleza de la cuenta es obligatoria")
    @Column(nullable = false, length = 20)
    private AccountNature accountNature;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AccountStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.status = AccountStatus.ACTIVE;
    }


}