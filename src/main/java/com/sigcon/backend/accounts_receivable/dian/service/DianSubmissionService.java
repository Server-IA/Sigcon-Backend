package com.sigcon.backend.accounts_receivable.dian.service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.sigcon.backend.accounts_receivable.dian.submissions.domain.model.DianInvoiceSubmission;
import com.sigcon.backend.accounts_receivable.dian.submissions.domain.model.DianSubmissionStatus;
import com.sigcon.backend.accounts_receivable.dian.submissions.domain.repository.DianInvoiceSubmissionRepository;
import com.sigcon.backend.accounts_receivable.sales_invoices.domain.model.SalesInvoice;
import com.sigcon.backend.accounts_receivable.sales_invoices.domain.repository.SalesInvoiceRepository;
import com.sigcon.backend.utils.SuccessRespondJson;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio de envio de facturas electronicas al proveedor tecnologico (PSE)
 * y a la DIAN (AR-15).
 *
 * <p><b>Importante:</b> esta clase es una SIMULACION del envio al PSE para
 * entornos de prueba. En produccion se debe integrar el cliente SOAP del
 * proveedor tecnologico correspondiente y manejar las respuestas reales
 * (ApplicationResponse, AttachedDocument). Hasta esa integracion, el metodo
 * {@link #submit(Long)} retorna ACCEPTED despues de una espera simulada.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DianSubmissionService {

    private final DianInvoiceSubmissionRepository submissionRepository;
    private final SalesInvoiceRepository salesInvoiceRepository;

    /** Tiempo de espera simulado del PSE en milisegundos. */
    private static final long MOCK_PSE_DELAY_MS = 2000L;

    /**
     * Dispara el envio asincrono al PSE. Si no existe aun un submission para la
     * factura lanza excepcion indicando que se debe generar el XML primero.
     *
     * @param invoiceId id de la factura de venta
     * @return respuesta HTTP con el submission en estado PENDING
     */
    public ResponseEntity<?> submit(Long invoiceId) {
        DianInvoiceSubmission last = submissionRepository
                .findFirstBySalesInvoiceIdAndDeletedAtIsNullOrderByIdDesc(invoiceId)
                .orElseThrow(() -> new IllegalStateException(
                        "Debe generar el XML de la factura antes de enviarla a la DIAN"));

        if (last.getSubmissionStatus() == DianSubmissionStatus.ACCEPTED) {
            throw new IllegalStateException("La factura ya fue enviada y aceptada por la DIAN");
        }

        last.setSubmittedAt(LocalDateTime.now());
        last.setAttemptCount(last.getAttemptCount() + 1);
        last.setSubmissionStatus(DianSubmissionStatus.PENDING);
        submissionRepository.save(last);

        Long submissionId = last.getId();
        CompletableFuture.runAsync(() -> processSubmissionAsync(submissionId));

        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Envio DIAN encolado (simulacion de PSE)"),
                Optional.of(last)));
    }

    /**
     * Procesamiento asincrono del envio. Marca como ACCEPTED luego de la espera
     * simulada y actualiza la factura (xmlSent=true).
     */
    @Async
    @Transactional
    public void processSubmissionAsync(Long submissionId) {
        // Multi-tenant (Bloque G fix): thread async no hereda TenantContext. El
        // submission tiene company_id en BD desde V10-C; lo recuperamos con
        // modo platform admin (bypass del @Filter) y luego lo fijamos para el
        // resto de la ejecucion. De otro modo findById fallaria con 404 o el
        // update sobreescribiria una empresa incorrecta.
        com.sigcon.backend.platform.tenant.TenantContext.setPlatformAdmin(true);
        Long companyId;
        try {
            companyId = submissionRepository.findById(submissionId)
                    .map(DianInvoiceSubmission::getCompanyId).orElse(null);
        } finally {
            com.sigcon.backend.platform.tenant.TenantContext.clear();
        }
        com.sigcon.backend.platform.tenant.TenantContext.setCompanyId(companyId);
        try {
            processSubmissionAsyncInternal(submissionId);
        } finally {
            com.sigcon.backend.platform.tenant.TenantContext.clear();
        }
    }

    private void processSubmissionAsyncInternal(Long submissionId) {
        try {
            Thread.sleep(MOCK_PSE_DELAY_MS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        DianInvoiceSubmission s = submissionRepository.findById(submissionId).orElse(null);
        if (s == null) return;
        s.setSubmissionStatus(DianSubmissionStatus.ACCEPTED);
        s.setDianResponse("Simulacion PSE: documento aceptado. TrackId=" + s.getTrackId());
        s.setRespondedAt(LocalDateTime.now());
        submissionRepository.save(s);

        SalesInvoice invoice = salesInvoiceRepository.findById(s.getSalesInvoiceId()).orElse(null);
        if (invoice != null) {
            invoice.setXmlSent(true);
            invoice.setCufe(s.getCufe());
            salesInvoiceRepository.save(invoice);
        }
        log.info("Envio DIAN {} marcado como ACCEPTED (simulacion)", submissionId);
    }

    /**
     * Retorna el ultimo submission registrado para una factura.
     */
    public ResponseEntity<?> getStatus(Long invoiceId) {
        DianInvoiceSubmission last = submissionRepository
                .findFirstBySalesInvoiceIdAndDeletedAtIsNullOrderByIdDesc(invoiceId)
                .orElseThrow(() -> new IllegalStateException(
                        "No se ha generado envio DIAN para esta factura"));
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Estado del envio DIAN"), Optional.of(last)));
    }
}
