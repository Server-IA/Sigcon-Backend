package com.sigcon.backend.lists_accounting.types_of_currency.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "cfg_currency_types", uniqueConstraints = {
        @UniqueConstraint(name = "uk_currency_iso_code", columnNames = "iso_code"),
        @UniqueConstraint(name = "uk_currency_name", columnNames = "name")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CurrencyType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "iso_code", nullable = false, length = 20)
    @NotBlank(message = "El código ISO de la moneda es obligatorio")
    @Pattern(regexp = "^([A-Z]{3}|[A-Z]{3}_DEL\\d+)$", message = "El código ISO debe tener exactamente 3 letras mayúsculas (formato ISO 4217)")
    private String isoCode;

    @Column(nullable = false, length = 100)
    @NotBlank(message = "El nombre de la moneda es obligatorio")
    @Size(min = 1, max = 100, message = "El nombre de la moneda debe tener máximo 100 caracteres")
    private String name;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(nullable = false, columnDefinition = "boolean default true")
    @Builder.Default
    private Boolean active = true;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.active == null) {
            this.active = true;
        }
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
