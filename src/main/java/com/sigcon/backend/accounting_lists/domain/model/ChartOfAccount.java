package com.sigcon.backend.accounting_lists.domain.model;

import com.sigcon.backend.accounting_lists.domain.model.enums.AccountClass;
import com.sigcon.backend.accounting_lists.domain.model.enums.AccountLevel;
import com.sigcon.backend.accounting_lists.domain.model.enums.AccountNature;
import com.sigcon.backend.accounting_lists.domain.model.enums.AccountStatus;
import jakarta.persistence.*;
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
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChartOfAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String code; //(e.g. 1105)

    @Column(nullable = false, length = 100)
    private String name; //(e.g. Cash)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AccountClass accountClass;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountLevel accountLevel;

    @Enumerated(EnumType.STRING)
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
