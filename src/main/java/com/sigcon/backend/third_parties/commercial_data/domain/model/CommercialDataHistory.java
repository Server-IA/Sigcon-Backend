package com.sigcon.backend.third_parties.commercial_data.domain.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

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

/**
 * Registro inmutable de cambios realizados sobre datos comerciales.
 * No se aplica soft delete ya que es auditoria permanente.
 */
@Entity
@Table(name = "commercial_data_history")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CommercialDataHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID del registro de datos comerciales al que pertenece el cambio */
    @Column(name = "commercial_data_id", nullable = false)
    private Long commercialDataId;

    /** Nombre del campo que cambio */
    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;

    /** Valor anterior del campo (texto) */
    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    /** Nuevo valor del campo (texto) */
    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    /** ID del usuario que realizo el cambio */
    @Column(name = "changed_by")
    private Long changedBy;

    /** Fecha y hora del cambio */
    @Column(name = "changed_at", nullable = false)
    @CreationTimestamp
    private LocalDateTime changedAt;
}
