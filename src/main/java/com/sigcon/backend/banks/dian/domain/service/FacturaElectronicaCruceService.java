package com.sigcon.backend.banks.dian.domain.service;

import com.sigcon.backend.accounts_receivable.payments.application.CreateArPaymentRequest;
import com.sigcon.backend.accounts_receivable.payments.domain.service.ArPaymentService;
import com.sigcon.backend.accounts_receivable.sales_invoices.domain.model.SalesInvoice;
import com.sigcon.backend.accounts_receivable.sales_invoices.domain.repository.SalesInvoiceRepository;
import com.sigcon.backend.audit.domain.model.enums.AuditAction;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.model.enums.AuditSeverity;
import com.sigcon.backend.audit.domain.service.AuditLogService;
import com.sigcon.backend.banks.financialmovements.domain.model.FinancialMovement;
import com.sigcon.backend.banks.financialmovements.domain.repository.FinancialMovementRepository;
import com.sigcon.backend.banks.matching.domain.model.Emparejamiento;
import com.sigcon.backend.banks.matching.domain.model.EmparejamientoDetalle;
import com.sigcon.backend.banks.matching.domain.repository.EmparejamientoDetalleRepository;
import com.sigcon.backend.banks.matching.domain.repository.EmparejamientoRepository;
import com.sigcon.backend.invoices.ap_payments.application.CreateApPaymentRequest;
import com.sigcon.backend.invoices.ap_payments.domain.service.ApPaymentService;
import com.sigcon.backend.invoices.domain.model.Invoices;
import com.sigcon.backend.invoices.domain.repository.InvoiceRepository;
import com.sigcon.backend.utils.UserUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * BNK-HU-078: cruce de movimientos del extracto con facturas electrónicas (CxC/CxP),
 * aplicando el cobro/pago, marcando la factura y dejando la trazabilidad para los
 * reportes de cumplimiento DIAN (art. 616-1 ET, Resolución 000165 de 2023).
 *
 * <p>Reutiliza los servicios existentes {@code ArPaymentService}/{@code ApPaymentService}
 * (que generan el comprobante de recaudo/pago en BORRADOR y aplican el saldo a la factura)
 * y el modelo de emparejamientos de la conciliación.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FacturaElectronicaCruceService {

    private static final BigDecimal TOL = new BigDecimal("0.01");
    private static final int VENTANA_DIAS = 15; // HU-078 E1: ventana ±15 días

    private final FinancialMovementRepository movementRepository;
    private final SalesInvoiceRepository salesInvoiceRepository;
    private final InvoiceRepository apInvoiceRepository;
    private final ArPaymentService arPaymentService;
    private final ApPaymentService apPaymentService;
    private final EmparejamientoRepository emparejamientoRepository;
    private final EmparejamientoDetalleRepository detalleRepository;
    private final AuditLogService auditLogService;
    private final UserUtil userUtil;

    // ===================== E1/E2: sugerir facturas =====================

    /**
     * HU-078 E1 (cobros CxC) / E2 (pagos CxP): sugiere facturas pendientes que coinciden
     * con un movimiento del extracto, por monto (±tolerancia) y ventana ±15 días,
     * priorizando por NIT detectado.
     */
    public Map<String, Object> sugerir(Long movementId) {
        FinancialMovement m = loadMovement(movementId);
        BigDecimal monto = m.getAmount() != null ? m.getAmount().abs() : BigDecimal.ZERO;
        LocalDate from = m.getMovementDate().minusDays(VENTANA_DIAS);
        LocalDate to = m.getMovementDate().plusDays(VENTANA_DIAS);
        boolean esCobro = m.getAmount() != null && m.getAmount().compareTo(BigDecimal.ZERO) > 0;
        String nit = m.getNitDetectado();

        List<Map<String, Object>> sugerencias = new ArrayList<>();
        if (esCobro) {
            for (SalesInvoice s : salesInvoiceRepository.findPendingBetween(from, to)) {
                BigDecimal saldo = s.getBalanceDue() != null ? s.getBalanceDue() : BigDecimal.ZERO;
                String invNit = s.getThirdParty() != null ? s.getThirdParty().getNit() : null;
                sugerencias.add(buildSugerencia("AR", s.getId(), s.getInvoiceNumber(), s.getInvoiceDate(),
                        s.getThirdParty() != null ? s.getThirdParty().getBusinessName() : null, invNit,
                        saldo, monto, nit, s.getDianStatus() != null ? s.getDianStatus().name() : null));
            }
        } else {
            for (Invoices i : apInvoiceRepository.findPendingBetween(from, to)) {
                BigDecimal saldo = i.getBalanceDue() != null ? BigDecimal.valueOf(i.getBalanceDue()) : BigDecimal.ZERO;
                String invNit = i.getThirdParty() != null ? i.getThirdParty().getNit() : null;
                sugerencias.add(buildSugerencia("AP", i.getId(), i.getSupplierInvoiceNumber(), i.getInvoiceDate(),
                        i.getThirdParty() != null ? i.getThirdParty().getBusinessName() : null, invNit,
                        saldo, monto, nit, null));
            }
        }
        sugerencias.sort((a, b) -> Integer.compare((int) b.get("score"), (int) a.get("score")));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("movimientoId", m.getId());
        out.put("fecha", m.getMovementDate());
        out.put("monto", monto);
        out.put("tipo", esCobro ? "COBRO_CXC" : "PAGO_CXP");
        out.put("nitDetectado", nit);
        out.put("sugerencias", sugerencias);
        return out;
    }

    private Map<String, Object> buildSugerencia(String origen, Long invId, String numero, LocalDate fecha,
                                                String tercero, String invNit, BigDecimal saldo,
                                                BigDecimal monto, String nitMov, String dianStatus) {
        int score = 60;
        if (saldo.subtract(monto).abs().compareTo(TOL) <= 0) score += 30;       // monto exacto
        else if (saldo.compareTo(monto) >= 0) score += 10;                       // alcanza para pago parcial
        boolean nitMatch = nitMov != null && nitMov.equals(invNit);
        if (nitMatch) score += 10;
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("origen", origen);
        s.put("invoiceId", invId);
        s.put("numero", numero);
        s.put("fecha", fecha);
        s.put("tercero", tercero);
        s.put("nit", invNit);
        s.put("saldo", saldo);
        s.put("dianStatus", dianStatus);
        s.put("nitMatch", nitMatch);
        s.put("score", Math.min(score, 100));
        return s;
    }

    // ===================== E3/E4/E5/E8: aplicar cruce 1:1 =====================

    /**
     * HU-078 E3/E4/E5/E8: aplica el cobro/pago de un movimiento del extracto a una factura
     * electrónica. Soporta pago parcial (E5): si el saldo de la factura supera el monto del
     * extracto, la factura queda con saldo pendiente y el movimiento se concilia totalmente.
     */
    @Transactional
    public Map<String, Object> aplicarCruce(Long movementId, Long invoiceId) {
        FinancialMovement m = loadMovement(movementId);
        boolean esCobro = m.getAmount() != null && m.getAmount().compareTo(BigDecimal.ZERO) > 0;
        BigDecimal montoExtracto = m.getAmount() != null ? m.getAmount().abs() : BigDecimal.ZERO;

        String numero;
        BigDecimal aplicado;
        if (esCobro) {
            SalesInvoice s = salesInvoiceRepository.findById(invoiceId)
                    .orElseThrow(() -> new IllegalArgumentException("Factura de venta no encontrada: " + invoiceId));
            BigDecimal saldo = s.getBalanceDue() != null ? s.getBalanceDue() : BigDecimal.ZERO;
            aplicado = montoExtracto.min(saldo); // E5: pago parcial
            aplicarCobroAr(invoiceId, aplicado, m);
            numero = s.getInvoiceNumber();
        } else {
            Invoices i = apInvoiceRepository.findById(invoiceId)
                    .orElseThrow(() -> new IllegalArgumentException("Factura de compra no encontrada: " + invoiceId));
            BigDecimal saldo = i.getBalanceDue() != null ? BigDecimal.valueOf(i.getBalanceDue()) : BigDecimal.ZERO;
            aplicado = montoExtracto.min(saldo);
            aplicarPagoAp(invoiceId, aplicado, m);
            numero = i.getSupplierInvoiceNumber();
        }

        // E3: marcar movimiento conciliado + emparejamiento (trazabilidad cruce↔factura).
        crearEmparejamientoCruce(m, "Cruce factura DIAN " + numero
                + " (factura #" + invoiceId + ", aplicado $" + aplicado + ")");

        // E8: auditar el cruce (acción EMPAREJAR mapeada a UPDATE; mensaje literal HU).
        auditLogService.register(AuditAction.UPDATE, AuditModule.BNK, AuditSeverity.LOW,
                "FinancialMovement", m.getId(),
                "EMPAREJAR · Cruce con factura electrónica DIAN " + numero
                        + " (factura_id=" + invoiceId + ", movimiento_extracto_id=" + m.getId()
                        + ", aplicado=$" + aplicado + ")",
                null, null, null);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("movimientoId", m.getId());
        out.put("invoiceId", invoiceId);
        out.put("numeroFactura", numero);
        out.put("tipo", esCobro ? "COBRO_CXC" : "PAGO_CXP");
        out.put("montoAplicado", aplicado);
        out.put("parcial", aplicado.compareTo(montoExtracto) < 0 || montoExtracto.compareTo(aplicado) > 0
                ? false : aplicado.compareTo(montoExtracto) == 0 ? false : true);
        out.put("mensaje", "Cruce aplicado. La factura quedó marcada como "
                + (esCobro ? "cobrada/parcial en CxC" : "pagada/parcial en CxP") + " y el comprobante en BORRADOR.");
        return out;
    }

    // ===================== E6: 1 pago → N facturas =====================

    /**
     * HU-078 E6: aplica un solo movimiento del extracto a varias facturas del mismo origen.
     * Valida Σ saldos ≈ monto (con tolerancia) o aplica parcial sobre la última.
     */
    @Transactional
    public Map<String, Object> aplicarCruceMultiple(Long movementId, List<Long> invoiceIds) {
        if (invoiceIds == null || invoiceIds.isEmpty())
            throw new IllegalArgumentException("Debe seleccionar al menos una factura.");
        FinancialMovement m = loadMovement(movementId);
        boolean esCobro = m.getAmount() != null && m.getAmount().compareTo(BigDecimal.ZERO) > 0;
        BigDecimal restante = m.getAmount() != null ? m.getAmount().abs() : BigDecimal.ZERO;

        List<Map<String, Object>> aplicaciones = new ArrayList<>();
        List<String> numeros = new ArrayList<>();
        for (Long invId : invoiceIds) {
            if (restante.compareTo(TOL) <= 0) break;
            BigDecimal saldo;
            String numero;
            if (esCobro) {
                SalesInvoice s = salesInvoiceRepository.findById(invId)
                        .orElseThrow(() -> new IllegalArgumentException("Factura de venta no encontrada: " + invId));
                saldo = s.getBalanceDue() != null ? s.getBalanceDue() : BigDecimal.ZERO;
                numero = s.getInvoiceNumber();
            } else {
                Invoices i = apInvoiceRepository.findById(invId)
                        .orElseThrow(() -> new IllegalArgumentException("Factura de compra no encontrada: " + invId));
                saldo = i.getBalanceDue() != null ? BigDecimal.valueOf(i.getBalanceDue()) : BigDecimal.ZERO;
                numero = i.getSupplierInvoiceNumber();
            }
            BigDecimal aplicar = restante.min(saldo);
            if (aplicar.compareTo(TOL) <= 0) continue;
            if (esCobro) aplicarCobroAr(invId, aplicar, m); else aplicarPagoAp(invId, aplicar, m);
            restante = restante.subtract(aplicar);
            numeros.add(numero);
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("invoiceId", invId); a.put("numero", numero); a.put("aplicado", aplicar);
            aplicaciones.add(a);
        }
        if (aplicaciones.isEmpty())
            throw new IllegalStateException("Ninguna factura tenía saldo aplicable.");

        crearEmparejamientoCruce(m, "Cruce consolidado " + (esCobro ? "CxC" : "CxP")
                + " con " + aplicaciones.size() + " facturas DIAN: " + String.join(", ", numeros));
        auditLogService.register(AuditAction.UPDATE, AuditModule.BNK, AuditSeverity.MEDIUM,
                "FinancialMovement", m.getId(),
                "EMPAREJAR · Cruce con facturas electrónicas DIAN (UNO_A_N): " + String.join(", ", numeros)
                        + " (movimiento_extracto_id=" + m.getId() + ")",
                null, null, null);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("movimientoId", m.getId());
        out.put("facturasAplicadas", aplicaciones.size());
        out.put("detalle", aplicaciones);
        out.put("remanente", restante);
        return out;
    }

    // ===================== E7: reporte de cumplimiento =====================

    /** HU-078 E7: reporte facturación electrónica vs cobros del período. */
    public Map<String, Object> reporteCumplimiento(int year, Integer month) {
        LocalDate from = month != null ? LocalDate.of(year, month, 1) : LocalDate.of(year, 1, 1);
        LocalDate to = month != null ? from.withDayOfMonth(from.lengthOfMonth()) : LocalDate.of(year, 12, 31);

        // CxC (ventas)
        List<SalesInvoice> ar = salesInvoiceRepository.findByInvoiceDateBetween(from, to);
        int emitidas = 0, cobradas = 0, pendientes = 0, anuladas = 0;
        BigDecimal totalEmitido = BigDecimal.ZERO, totalCobrado = BigDecimal.ZERO, totalPendiente = BigDecimal.ZERO;
        for (SalesInvoice s : ar) {
            String st = s.getStatus() != null ? s.getStatus().name() : "";
            if ("VOIDED".equals(st)) { anuladas++; continue; }
            if ("DRAFT".equals(st)) continue;
            emitidas++;
            BigDecimal total = s.getTotalAmount() != null ? s.getTotalAmount() : BigDecimal.ZERO;
            BigDecimal saldo = s.getBalanceDue() != null ? s.getBalanceDue() : BigDecimal.ZERO;
            totalEmitido = totalEmitido.add(total);
            totalCobrado = totalCobrado.add(total.subtract(saldo));
            totalPendiente = totalPendiente.add(saldo);
            if ("PAID".equals(st) || "SETTLED".equals(st)) cobradas++;
            else pendientes++;
        }

        // CxP (compras)
        List<Invoices> ap = apInvoiceRepository.findByInvoiceDateBetweenForReport(from, to);
        int apEmitidas = 0, apPagadas = 0, apPendientes = 0, apAnuladas = 0;
        for (Invoices i : ap) {
            String st = i.getStatus() != null ? i.getStatus().name() : "";
            if ("VOIDED".equals(st)) { apAnuladas++; continue; }
            apEmitidas++;
            if ("PAID".equals(st) || "SETTLED".equals(st)) apPagadas++; else apPendientes++;
        }

        Map<String, Object> cxc = new LinkedHashMap<>();
        cxc.put("emitidas", emitidas); cxc.put("cobradas", cobradas);
        cxc.put("pendientes", pendientes); cxc.put("anuladas", anuladas);
        cxc.put("totalEmitido", totalEmitido); cxc.put("totalCobrado", totalCobrado);
        cxc.put("totalPendiente", totalPendiente);

        Map<String, Object> cxp = new LinkedHashMap<>();
        cxp.put("emitidas", apEmitidas); cxp.put("pagadas", apPagadas);
        cxp.put("pendientes", apPendientes); cxp.put("anuladas", apAnuladas);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("year", year); out.put("month", month);
        out.put("desde", from); out.put("hasta", to);
        out.put("cxc", cxc); out.put("cxp", cxp);
        return out;
    }

    // ===================== helpers =====================

    private void aplicarCobroAr(Long invoiceId, BigDecimal amount, FinancialMovement m) {
        CreateArPaymentRequest req = CreateArPaymentRequest.builder()
                .invoiceId(invoiceId).amount(amount).paymentDate(m.getMovementDate())
                .paymentMethod("TRANSFERENCIA")
                .bankAccountId(m.getBankAccount() != null ? m.getBankAccount().getId() : null)
                .bankMovementId(m.getId())
                .paymentReference(m.getExternalReference())
                .notes("Cruce conciliación bancaria (factura electrónica DIAN)")
                .build();
        ResponseEntity<?> resp = arPaymentService.registerPayment(req);
        if (!resp.getStatusCode().is2xxSuccessful())
            throw new IllegalStateException("No se pudo aplicar el cobro a la factura " + invoiceId
                    + ": " + bodyMsg(resp));
    }

    private void aplicarPagoAp(Long invoiceId, BigDecimal amount, FinancialMovement m) {
        CreateApPaymentRequest req = CreateApPaymentRequest.builder()
                .invoiceId(invoiceId).amount(amount).paymentDate(m.getMovementDate())
                .paymentMethod("TRANSFERENCIA")
                .bankAccountId(m.getBankAccount() != null ? m.getBankAccount().getId() : null)
                .paymentReference(m.getExternalReference())
                .notes("Cruce conciliación bancaria (factura electrónica DIAN)")
                .build();
        ResponseEntity<?> resp = apPaymentService.registerPayment(req);
        if (!resp.getStatusCode().is2xxSuccessful())
            throw new IllegalStateException("No se pudo aplicar el pago a la factura " + invoiceId
                    + ": " + bodyMsg(resp));
    }

    private void crearEmparejamientoCruce(FinancialMovement m, String motivo) {
        BigDecimal abs = m.getAmount() != null ? m.getAmount().abs() : BigDecimal.ZERO;
        String username = currentUser();
        Emparejamiento emp = Emparejamiento.builder()
                .companyId(m.getCompanyId())
                .cuentaBancariaId(m.getBankAccount() != null ? m.getBankAccount().getId() : null)
                .tipoEmparejamiento("CRUCE_FE")
                .metodo("FACTURA_ELECTRONICA")
                .score(100).estado("CONFIRMADO")
                .sumaExtracto(abs).sumaLibros(abs).diferencia(BigDecimal.ZERO)
                .motivoMatchManual(motivo)
                .confirmadoAt(LocalDateTime.now()).confirmadoBy(username)
                .build();
        emp = emparejamientoRepository.save(emp);
        detalleRepository.save(EmparejamientoDetalle.builder()
                .companyId(emp.getCompanyId()).emparejamientoId(emp.getId())
                .financialMovementId(m.getId()).lado("EXTRACTO").monto(m.getAmount()).build());
        m.setEstadoConciliacion("CONCILIADO");
        movementRepository.save(m);
    }

    private FinancialMovement loadMovement(Long movementId) {
        FinancialMovement m = movementRepository.findById(movementId)
                .orElseThrow(() -> new IllegalArgumentException("Movimiento no encontrado: " + movementId));
        if ("CONCILIADO".equalsIgnoreCase(String.valueOf(m.getEstadoConciliacion())))
            throw new IllegalStateException("El movimiento #" + movementId + " ya está conciliado.");
        return m;
    }

    private String bodyMsg(ResponseEntity<?> resp) {
        Object b = resp.getBody();
        return b != null ? b.toString() : ("HTTP " + resp.getStatusCode().value());
    }

    private String currentUser() {
        try { var u = userUtil.getUser(); return u != null ? u.getUsername() : "sistema"; }
        catch (Exception e) { return "sistema"; }
    }
}
