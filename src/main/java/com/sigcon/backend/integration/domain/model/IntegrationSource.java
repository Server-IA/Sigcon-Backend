package com.sigcon.backend.integration.domain.model;

import com.sigcon.backend.integration.domain.model.enums.SourceOrigin;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Trazabilidad de origen para entidades que pueden crearse manualmente o
 * recibirse via integracion AAEF.
 *
 * <p>Este objeto embebible se incluye como {@code @Embedded} en las entidades:
 * SalesInvoice, Invoices, ArPayment, ApPayment, ArAdvance, ApAdvance,
 * ArCreditDebitNote, ApCreditDebitNote.
 *
 * <p>Las columnas se mapean a: {@code source}, {@code external_id}, {@code exchange_id}
 * (creadas en migracion V32).
 */
@Embeddable
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IntegrationSource {

    /** MANUAL (por defecto) o AAEF. */
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "source", length = 20)
    private SourceOrigin source = SourceOrigin.MANUAL;

    /** ID externo del documento segun AgroFusion (null para documentos MANUAL). */
    @Column(name = "external_id", length = 100)
    private String externalId;

    /** ID del lote AAEF que origino el documento (null para documentos MANUAL). */
    @Column(name = "exchange_id", length = 64)
    private String exchangeId;
}
