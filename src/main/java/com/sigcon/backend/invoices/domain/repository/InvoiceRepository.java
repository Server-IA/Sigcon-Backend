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

    /**
     * HU-AP-01 (Bloque AT): unicidad de supplierInvoiceNumber por empresa.
     * QA reporto que en la misma empresa no se podian distinguir 2 facturas con
     * el mismo numero. Esta query SI usa @Filter("tenantFilter") (JPQL),
     * limitando la busqueda a la empresa actual.
     */
    @Query("SELECT COUNT(i) > 0 FROM Invoices i WHERE i.supplierInvoiceNumber = :supplierInvoiceNumber "
         + "AND i.deletedAt IS NULL")
    boolean existsBySupplierInvoiceNumberAndDeletedAtIsNull(String supplierInvoiceNumber);

    /**
     * HU-AP-01 (Bloque AT): unicidad de resolutionInvoice (resolucion DIAN) por empresa.
     */
    @Query("SELECT COUNT(i) > 0 FROM Invoices i WHERE i.resolutionInvoice = :resolutionInvoice "
         + "AND i.deletedAt IS NULL")
    boolean existsByResolutionInvoiceAndDeletedAtIsNull(String resolutionInvoice);

    /**
     * QA-BLOQUE-AM (2026-04-29): obtiene el MAX(resolution) numerico para el tipo indicado,
     * ignorando seeds con valores no-parseables (ej. "FC-QA6-003" del seed V9-ZZC).
     * Usado por createInvoiceFromAaef para no romper con MAPPING_ERROR cuando llega un lote
     * AAEF a una empresa que tiene seeds QA con resolutions alfanumericas.
     * Filtra por company_id explicitamente porque @Filter("tenantFilter") no aplica a queries nativas.
     */
    @Query(value = "SELECT COALESCE(MAX(CAST(resolution AS INTEGER)), 0) "
                 + "FROM invoices "
                 + "WHERE type_invoice_id = :typeInvoiceId "
                 + "AND company_id = :companyId "
                 + "AND deleted_at IS NULL "
                 + "AND resolution ~ '^[0-9]+$'", nativeQuery = true)
    Integer findMaxNumericResolution(Long typeInvoiceId, Long companyId);

    /**
     * QA-2026-05-05: soft-delete via UPDATE nativo. Usar este metodo cuando
     * `deleteById` choca con la combinacion `@SQLDelete + @Version` (Hibernate
     * intenta pasar 2 parametros al SQL custom de 1 placeholder y falla con
     * "column index out of range: 2").
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query(value = "UPDATE invoices SET deleted_at = NOW() WHERE id = :id", nativeQuery = true)
    void softDeleteById(Long id);
}
