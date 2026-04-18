package com.sigcon.backend.third_parties.change_history.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Entidad de historial de cambios de terceros.
 * Registra cada modificacion realizada sobre los campos de un tercero.
 * Los registros de auditoria son permanentes (sin soft delete).
 */
@Entity
@Table(name = "third_party_change_history")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ThirdPartyChangeHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "third_party_id", nullable = false)
    private Long thirdPartyId;

    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "changed_by")
    private Long changedBy;

    @Column(name = "changed_at", nullable = false)
    @CreationTimestamp
    private LocalDateTime changedAt;
}
