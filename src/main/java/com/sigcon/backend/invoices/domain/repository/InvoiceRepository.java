package com.sigcon.backend.invoices.domain.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.sigcon.backend.invoices.domain.model.Invoices;

public interface InvoiceRepository extends JpaRepository<Invoices, Long>, JpaSpecificationExecutor<Invoices> {

    /**
     * HU-INT-RF-05: busca una factura de compra por su externalId (DocumentId AAEF).
     */
    Optional<Invoices> findByIntegrationSource_ExternalIdAndDeletedAtIsNull(String externalId);

    /**
     * AP-01: Obtiene la factura mas reciente del tipo indicado para calcular
     * el siguiente numero consecutivo de resolucion interna.
     * Antes devolvia un unico registro y fallaba con "non-unique result" al
     * existir varias facturas del mismo tipo.
     */
    Invoices findFirstByTypeInvoiceIdAndDeletedAtIsNullOrderByIdDesc(Long typeInvoiceId);

    /**
     * TER-10: Verifica si un tercero tiene facturas activas asociadas.
     */
    boolean existsByThirdPartyIdAndDeletedAtIsNull(Long thirdPartyId);

    /**
     * AP-01 E2 / AP-24 E5: Verifica si ya existe una factura con el mismo numero de proveedor
     * para el mismo tercero en el anio fiscal indicado.
     */
    @Query("SELECT COUNT(i) > 0 FROM Invoices i WHERE i.supplierInvoiceNumber = :supplierInvoiceNumber "
         + "AND i.thirdParty.id = :thirdPartyId AND YEAR(i.invoiceDate) = :year AND i.deletedAt IS NULL")
    boolean existsBySupplierInvoiceNumberAndThirdPartyAndYear(String supplierInvoiceNumber, Long thirdPartyId, Integer year);
}
