package com.sigcon.backend.invoices.purchase_orders.domain.service;

import java.math.BigDecimal;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.sigcon.backend.invoices.domain.model.Invoices;
import com.sigcon.backend.invoices.domain.repository.InvoiceRepository;
import com.sigcon.backend.invoices.purchase_orders.application.CreateGoodsReceiptLineRequest;
import com.sigcon.backend.invoices.purchase_orders.application.CreateGoodsReceiptRequest;
import com.sigcon.backend.invoices.purchase_orders.application.GoodsReceiptDTO;
import com.sigcon.backend.invoices.purchase_orders.application.GoodsReceiptLineDTO;
import com.sigcon.backend.invoices.purchase_orders.application.LinkInvoiceRequest;
import com.sigcon.backend.invoices.purchase_orders.application.RejectGoodsReceiptRequest;
import com.sigcon.backend.invoices.purchase_orders.domain.model.GoodsReceipt;
import com.sigcon.backend.invoices.purchase_orders.domain.model.GoodsReceiptLine;
import com.sigcon.backend.invoices.purchase_orders.domain.model.PurchaseOrder;
import com.sigcon.backend.invoices.purchase_orders.domain.model.PurchaseOrderLine;
import com.sigcon.backend.invoices.purchase_orders.domain.model.GoodsReceiptInvoiceLink;
import com.sigcon.backend.invoices.purchase_orders.domain.repository.GoodsReceiptInvoiceLinkRepository;
import com.sigcon.backend.invoices.purchase_orders.domain.repository.GoodsReceiptLineRepository;
import com.sigcon.backend.invoices.purchase_orders.domain.repository.GoodsReceiptRepository;
import com.sigcon.backend.invoices.purchase_orders.domain.repository.PurchaseOrderLineRepository;
import com.sigcon.backend.invoices.purchase_orders.domain.repository.PurchaseOrderRepository;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.utils.SuccessRespondJson;
import com.sigcon.backend.utils.UserUtil;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio para la gestion de recepciones de bienes/servicios.
 * Valida que la orden de compra este aprobada y que las cantidades
 * recibidas no excedan las cantidades ordenadas.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoodsReceiptService {

    private final GoodsReceiptRepository receiptRepository;
    private final GoodsReceiptLineRepository receiptLineRepository;
    private final PurchaseOrderRepository orderRepository;
    private final PurchaseOrderLineRepository orderLineRepository;
    private final InvoiceRepository invoiceRepository;
    private final UserUtil userUtil;
    private final AuditPublisher auditPublisher;

    /**
     * QA-BLOQUE-AY HU-AP-19 (2026-05-06): repositorio de vinculaciones N:M
     * receipt<->invoice con monto facturado por link.
     */
    private final GoodsReceiptInvoiceLinkRepository receiptInvoiceLinkRepository;

    /**
     * QA-BLOQUE-AY HU-AP-21 (2026-05-05): repositorio de devoluciones para
     * generar consecutivo unico por empresa (DV-{año}{6dig}).
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.sigcon.backend.invoices.purchase_orders.domain.repository.GoodsReturnRepository goodsReturnRepository;

    private final DataTableSpecificationBuilder<GoodsReceipt> specBuilder = new DataTableSpecificationBuilder<>();

    /**
     * Registra una nueva recepcion de bienes/servicios.
     * Valida que la orden de compra este en estado APPROVED y que las
     * cantidades recibidas no excedan las cantidades de la orden.
     *
     * @param request datos de la recepcion y sus lineas
     * @return ResponseEntity con la recepcion creada
     * @throws IllegalArgumentException si la OC no existe, una linea no pertenece a la OC,
     *                                  o la cantidad excede lo pendiente
     * @throws IllegalStateException    si la OC no esta aprobada
     */
    @Transactional
    public ResponseEntity<?> createReceipt(CreateGoodsReceiptRequest request) {
        // 1. Validar orden de compra
        PurchaseOrder order = orderRepository.findById(request.getPurchaseOrderId())
                .orElseThrow(() -> new IllegalArgumentException("La orden de compra no fue encontrada"));

        // QA-BLOQUE-AY HU-AP-18 E2 (2026-05-06): permitir recepciones tambien
        // cuando la OC ya esta PARTIALLY_RECEIVED. El status se actualiza
        // automaticamente al recepcionar (RECEIVED si llego todo, PARTIALLY_RECEIVED
        // si aun falta). Solo se bloquea si la OC esta en estado distinto al
        // ciclo de recepcion (DRAFT, PENDING, REJECTED, CLOSED).
        String s = order.getStatus();
        if (!"APPROVED".equals(s) && !"PARTIALLY_RECEIVED".equals(s)) {
            throw new IllegalStateException(
                "Solo se pueden crear recepciones para ordenes APROBADAS o PARCIALMENTE RECIBIDAS (estado actual: " + s + ")");
        }

        // 2. Generar numero de recepcion
        String receiptNumber = generateReceiptNumber();

        // 3. Construir la recepcion
        GoodsReceipt receipt = GoodsReceipt.builder()
                .purchaseOrder(order)
                .receiptNumber(receiptNumber)
                .receiptDate(request.getReceiptDate())
                .status("RECEIVED")
                .notes(request.getNotes())
                .createdBy(userUtil.getUser().getId())
                .build();

        // 4. Validar y crear lineas
        for (CreateGoodsReceiptLineRequest lineReq : request.getLines()) {
            PurchaseOrderLine poLine = orderLineRepository.findById(lineReq.getPurchaseOrderLineId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "La linea de orden de compra " + lineReq.getPurchaseOrderLineId() + " no fue encontrada"));

            // Validar que la linea pertenece a la OC
            if (!poLine.getPurchaseOrder().getId().equals(order.getId())) {
                throw new IllegalArgumentException(
                        "La linea " + poLine.getId() + " no pertenece a la orden de compra " + order.getOrderNumber());
            }

            // Validar cantidad no exceda lo pendiente
            BigDecimal totalReceived = calculateTotalReceived(poLine.getId());
            BigDecimal pendingQty = poLine.getQuantity().subtract(totalReceived);
            if (lineReq.getQuantityReceived().compareTo(pendingQty) > 0) {
                throw new IllegalArgumentException(
                        "La cantidad recibida (" + lineReq.getQuantityReceived()
                                + ") excede la cantidad pendiente (" + pendingQty
                                + ") para la linea: " + poLine.getDescription());
            }

            GoodsReceiptLine grLine = GoodsReceiptLine.builder()
                    .goodsReceipt(receipt)
                    .purchaseOrderLine(poLine)
                    .quantityReceived(lineReq.getQuantityReceived())
                    .build();
            receipt.getLines().add(grLine);
        }

        receipt = receiptRepository.save(receipt);
        auditPublisher.publishCreate(AuditModule.AP, "GoodsReceipt", receipt.getId(), "GoodsReceipt creado id=" + receipt.getId());
        log.info("Recepcion {} creada para OC {}", receiptNumber, order.getOrderNumber());

        // QA-BLOQUE-AY HU-AP-18 E2 (2026-05-05): cuando lo recibido es menor a lo
        // pedido, marcar la OC como "PARTIALLY_RECEIVED" para que el front
        // muestre el estado correcto. Si todas las cantidades se completaron
        // (total recibido == total pedido por linea), la OC pasa a "RECEIVED".
        try {
            updatePurchaseOrderReceptionStatus(order);
        } catch (Exception ex) {
            log.warn("HU-AP-18 E2: no se pudo actualizar status de recepcion de OC {}: {}",
                    order.getOrderNumber(), ex.getMessage());
        }

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Recepcion registrada exitosamente"), Optional.of(toDTO(receipt))));
    }

    /**
     * QA-BLOQUE-AY HU-AP-18 E2: recalcula y persiste el estado de recepcion
     * de la OC en funcion de las recepciones registradas (no anuladas).
     *   - Sin recepciones aun -> APPROVED (sin cambio)
     *   - Recibido total < pedido total -> PARTIALLY_RECEIVED
     *   - Recibido total == pedido total -> RECEIVED
     */
    private void updatePurchaseOrderReceptionStatus(PurchaseOrder order) {
        List<PurchaseOrderLine> lines = orderLineRepository.findByPurchaseOrderId(order.getId());
        if (lines == null || lines.isEmpty()) return;
        BigDecimal totalOrdered = BigDecimal.ZERO;
        BigDecimal totalReceived = BigDecimal.ZERO;
        for (PurchaseOrderLine line : lines) {
            BigDecimal qty = line.getQuantity() != null ? line.getQuantity() : BigDecimal.ZERO;
            totalOrdered = totalOrdered.add(qty);
            totalReceived = totalReceived.add(calculateTotalReceived(line.getId()));
        }
        String newStatus;
        if (totalReceived.compareTo(BigDecimal.ZERO) <= 0) {
            return; // sin recepciones, no tocar
        } else if (totalReceived.compareTo(totalOrdered) >= 0) {
            newStatus = "RECEIVED";
        } else {
            newStatus = "PARTIALLY_RECEIVED";
        }
        if (!newStatus.equals(order.getStatus())) {
            order.setStatus(newStatus);
            orderRepository.save(order);
            log.info("HU-AP-18 E2: OC {} status -> {} (recibido {} de {})",
                    order.getOrderNumber(), newStatus, totalReceived, totalOrdered);
        }
    }

    /**
     * Consulta recepciones con paginacion y filtros DataTable.
     *
     * @param request parametros de busqueda y paginacion
     * @return listado paginado de recepciones
     */
    public ResponseEntity<?> getReceipts(DataTableRequest request) {
        int start = Math.max(0, request.getStart());
        int length = request.getLength();
        int safeLength = length <= 0 ? 20 : Math.min(length, 100);
        int page = start / safeLength;

        Pageable pageable = length == -1
                ? Pageable.unpaged()
                : PageRequest.of(page, safeLength);

        Specification<GoodsReceipt> spec = specBuilder.build(request);
        Page<GoodsReceiptDTO> data = receiptRepository.findAll(spec, pageable).map(this::toDTO);

        return ResponseEntity.ok(DataTableResponse.from(data, request.getDraw()));
    }

    /**
     * Obtiene una recepcion por su identificador con sus lineas de detalle.
     *
     * @param id identificador de la recepcion
     * @return ResponseEntity con la recepcion encontrada
     * @throws IllegalArgumentException si la recepcion no existe
     */
    public ResponseEntity<?> getReceiptById(Long id) {
        GoodsReceipt receipt = receiptRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La recepcion no fue encontrada"));
        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Recepcion encontrada"), Optional.of(toDTO(receipt))));
    }

    /**
     * Vincula una factura de compra a una recepcion para three-way match
     * (orden de compra - recepcion - factura).
     *
     * @param receiptId identificador de la recepcion
     * @param request   datos con el ID de la factura a vincular
     * @return ResponseEntity con la recepcion actualizada
     * @throws IllegalArgumentException si la recepcion o factura no existen
     * @throws IllegalStateException    si la recepcion ya tiene factura vinculada
     */
    @Transactional
    public ResponseEntity<?> linkToInvoice(Long receiptId, LinkInvoiceRequest request) {
        GoodsReceipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new IllegalArgumentException("La recepcion no fue encontrada"));

        // Validar que la factura existe
        Invoices invoice = invoiceRepository.findById(request.getInvoiceId())
                .orElseThrow(() -> new IllegalArgumentException("La factura no fue encontrada"));

        // AP-20 E2: Validar que el proveedor de la recepcion coincide con el de la factura
        if (receipt.getPurchaseOrder() != null && receipt.getPurchaseOrder().getThirdParty() != null
                && invoice.getThirdParty() != null) {
            Long poThirdPartyId = receipt.getPurchaseOrder().getThirdParty().getId();
            Long invoiceThirdPartyId = invoice.getThirdParty().getId();
            if (!poThirdPartyId.equals(invoiceThirdPartyId)) {
                throw new IllegalArgumentException(
                        "El proveedor de la factura no coincide con el de la recepción");
            }
        }

        // QA-BLOQUE-AY HU-AP-19 E1/E4/E5/E6 (2026-05-06): vinculacion parcial N:M.
        // 1) Calcular total recepcion (sumando lineas pedidas * precio).
        // 2) Calcular total ya facturado (sumando links activos).
        // 3) Validar idempotencia: no re-link mismo (receipt, invoice).
        // 4) E4: si total_facturado >= total_recepcion -> bloquear (mensaje literal).
        // 5) E3: si invoice.totalAmount > saldo_pendiente -> bloquear.
        // 6) E5: si invoice.totalAmount < saldo_pendiente -> warning informativo.
        java.math.BigDecimal receiptTotal = java.math.BigDecimal.ZERO;
        if (receipt.getLines() != null) {
            for (var rl : receipt.getLines()) {
                java.math.BigDecimal qty = rl.getQuantityReceived();
                if (qty == null) continue;
                java.math.BigDecimal price = java.math.BigDecimal.ZERO;
                if (rl.getPurchaseOrderLine() != null && rl.getPurchaseOrderLine().getUnitPrice() != null) {
                    price = rl.getPurchaseOrderLine().getUnitPrice();
                }
                receiptTotal = receiptTotal.add(qty.multiply(price));
            }
        }
        java.math.BigDecimal invoiceTotal = invoice.getTotalAmount() != null
                ? java.math.BigDecimal.valueOf(invoice.getTotalAmount())
                : java.math.BigDecimal.ZERO;

        // E1 idempotencia: si ya existe link activo para mismo (receipt, invoice), bloquear.
        if (receiptInvoiceLinkRepository
                .findFirstByReceiptIdAndInvoiceIdAndDeletedAtIsNull(receipt.getId(), invoice.getId())
                .isPresent()) {
            throw new IllegalStateException(
                "Esta factura ya esta vinculada a esta recepcion. Use otra factura "
                + "o ajuste el link existente.");
        }

        java.math.BigDecimal alreadyInvoiced = java.util.Optional.ofNullable(
                receiptInvoiceLinkRepository.sumInvoicedAmountByReceiptId(receipt.getId()))
                .orElse(java.math.BigDecimal.ZERO);
        java.math.BigDecimal pending = receiptTotal.subtract(alreadyInvoiced);

        // E4: si la recepcion ya esta totalmente facturada, bloquear.
        if (receiptTotal.signum() > 0
                && pending.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException(
                "Esta recepcion ya fue facturada en su totalidad");
        }

        // E3: el monto de la factura supera el saldo pendiente de la recepcion.
        String warning = null;
        if (receiptTotal.signum() > 0) {
            int cmp = invoiceTotal.compareTo(pending);
            if (cmp > 0) {
                throw new IllegalArgumentException(
                        "El monto de la factura supera el valor pendiente por facturar de la recepcion. "
                        + "Saldo pendiente: $" + pending + " - Factura: $" + invoiceTotal
                        + ". Verifique las cantidades o solicite una nota credito al proveedor.");
            } else if (cmp < 0) {
                // E5: factura inferior al saldo pendiente -> warning informativo
                java.math.BigDecimal diff = pending.subtract(invoiceTotal);
                warning = "El monto facturado ($" + invoiceTotal + ") es inferior al saldo pendiente "
                        + "de la recepcion ($" + pending + "). Diferencia: $" + diff
                        + ". La recepcion queda como Parcialmente facturada.";
                log.warn("HU-AP-19 E5 receipt={} invoice={} pending={} diff={}",
                        receipt.getId(), invoice.getId(), pending, diff);
            }
        }

        // Persistir el link (HU-AP-19 E6: multi-link)
        GoodsReceiptInvoiceLink link = GoodsReceiptInvoiceLink.builder()
                .receiptId(receipt.getId())
                .invoiceId(invoice.getId())
                .invoicedAmount(invoiceTotal)
                .notes(warning)
                .build();
        link = receiptInvoiceLinkRepository.save(link);

        // Compatibilidad con el modelo legacy: si el receipt aun no tiene
        // invoice_id, asignamos el del primer link (para que el listado y
        // reportes legacy sigan funcionando). En links posteriores se mantiene
        // el primero.
        if (receipt.getInvoiceId() == null) {
            receipt.setInvoiceId(invoice.getId());
            receipt = receiptRepository.save(receipt);
        }

        // Estado fully-invoiced si total facturado tras este link >= recepcion.
        java.math.BigDecimal totalAfterLink = alreadyInvoiced.add(invoiceTotal);
        boolean fullyInvoiced = receiptTotal.signum() > 0
                && totalAfterLink.compareTo(receiptTotal) >= 0;

        auditPublisher.publishCreate(AuditModule.AP, "GoodsReceiptInvoiceLink", link.getId(),
                "Link receipt #" + receipt.getId() + " <-> invoice #" + invoice.getId()
                + " | invoicedAmount=$" + invoiceTotal
                + " | totalFacturado=$" + totalAfterLink
                + " | totalRecepcion=$" + receiptTotal
                + (warning != null ? " | warning: " + warning : ""));
        log.info("HU-AP-19 link receipt={} invoice={} amount={} fullyInvoiced={}",
                receipt.getReceiptNumber(), invoice.getId(), invoiceTotal, fullyInvoiced);

        String successMsg;
        if (fullyInvoiced) {
            successMsg = "Factura vinculada exitosamente. La recepcion queda Totalmente facturada.";
        } else if (warning != null) {
            successMsg = "Factura vinculada con alerta: " + warning;
        } else {
            successMsg = "Factura vinculada exitosamente a la recepcion. Saldo pendiente por facturar: $"
                    + receiptTotal.subtract(totalAfterLink);
        }

        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("receiptId", receipt.getId());
        payload.put("invoiceId", invoice.getId());
        payload.put("linkId", link.getId());
        payload.put("invoicedAmount", invoiceTotal);
        payload.put("totalReceipt", receiptTotal);
        payload.put("totalInvoiced", totalAfterLink);
        payload.put("pendingToInvoice", receiptTotal.subtract(totalAfterLink));
        payload.put("fullyInvoiced", fullyInvoiced);
        payload.put("invoicedStatus", fullyInvoiced ? "FULLY_INVOICED"
                : (totalAfterLink.signum() > 0 ? "PARTIALLY_INVOICED" : "NOT_INVOICED"));
        if (warning != null) payload.put("warning", warning);
        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of(successMsg), Optional.of(payload)));
    }

    /**
     * AP-22: Rechaza o registra la devolucion de una recepcion de bienes/servicios.
     *
     * <p>Reglas de negocio:
     * <ul>
     *   <li>La recepcion debe existir y estar en estado RECEIVED (no previamente rechazada).</li>
     *   <li>La recepcion NO puede estar vinculada a una factura (invoice_id != null) —
     *       para rechazar una recepcion vinculada primero debe desvincularse.</li>
     *   <li>El motivo del rechazo es obligatorio (validacion en el DTO: min 20 chars).</li>
     * </ul>
     *
     * <p>La recepcion se marca como {@code REJECTED} y se registran auditores
     * ({@code rejectedAt}, {@code rejectedBy}, {@code rejectionReason}) preservando
     * el historial de la operacion.
     *
     * @param receiptId ID de la recepcion a rechazar
     * @param request   request con el motivo del rechazo
     * @return ResponseEntity con la recepcion marcada como REJECTED
     * @throws IllegalArgumentException si la recepcion no existe
     * @throws IllegalStateException    si la recepcion ya esta rechazada o vinculada a factura
     */
    @Transactional
    public ResponseEntity<?> rejectReceipt(Long receiptId, RejectGoodsReceiptRequest request) {
        GoodsReceipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new IllegalArgumentException("La recepcion no fue encontrada"));

        if ("REJECTED".equalsIgnoreCase(receipt.getStatus())) {
            throw new IllegalStateException("La recepcion ya esta rechazada");
        }

        // HU-AP-21 E3 (Bloque AR/AY): mensaje literal HU. Si la recepcion ya tiene
        // ALGUN link activo a factura, la devolucion fisica no procede aqui;
        // debe solicitarse una nota credito al proveedor.
        boolean hasInvoiceLink = receipt.getInvoiceId() != null
                || !receiptInvoiceLinkRepository.findByReceiptIdAndDeletedAtIsNull(receipt.getId()).isEmpty();
        if (hasInvoiceLink) {
            throw new IllegalStateException(
                    "No se puede devolver: los ítems seleccionados ya tienen una recepción "
                    + "con factura de compra asociada. Solicite una nota crédito al proveedor.");
        }

        receipt.setStatus("REJECTED");
        receipt.setRejectedAt(java.time.LocalDateTime.now());
        receipt.setRejectionReason(request.getReason());
        try {
            receipt.setRejectedBy(userUtil.getUser().getId());
        } catch (Exception ignored) {
            // sin usuario autenticado no bloquea la operacion
        }

        receipt = receiptRepository.save(receipt);
        log.info("Recepcion {} rechazada. Motivo: {}", receipt.getReceiptNumber(), request.getReason());

        // QA-BLOQUE-AS (2026-04-30): publicar auditoria de la devolucion.
        // Antes el reject no aparecia en la bitacora — solo CREATE de
        // GoodsReceipt y UPDATE de PurchaseOrder. La devolucion + motivo es
        // critica para trazabilidad legal y QA lo reporto explicitamente.
        auditPublisher.publishUpdate(AuditModule.AP, "GoodsReceipt", receipt.getId(),
                "Recepcion " + receipt.getReceiptNumber() + " RECHAZADA. Motivo: " + request.getReason());

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Recepcion rechazada exitosamente"), Optional.of(toDTO(receipt))));
    }

    /**
     * QA-BLOQUE-AY HU-AP-21 (2026-05-05): registra una devolucion (parcial o
     * total) sobre una recepcion existente. Cumple los escenarios:
     *
     * <ul>
     *   <li>E1: genera codigo unico DV-{año}{6dig}, actualiza saldos y audita.</li>
     *   <li>E2: si la cantidad devuelta &lt; total recibido en cualquier linea, la
     *       recepcion queda "PARTIALLY_RETURNED" y se mantiene el saldo
     *       disponible para futuras devoluciones.</li>
     *   <li>E3: bloqueada cuando la recepcion ya tiene factura asociada
     *       (mensaje literal HU).</li>
     *   <li>E4: si la cantidad a devolver supera lo recibido (menos lo ya
     *       devuelto), rechaza con mensaje literal HU.</li>
     * </ul>
     */
    @Transactional
    public ResponseEntity<?> createReturn(Long receiptId,
            com.sigcon.backend.invoices.purchase_orders.application.CreateGoodsReturnRequest request) {

        GoodsReceipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new IllegalArgumentException("La recepcion no fue encontrada"));

        if (receipt.getInvoiceId() != null) {
            throw new IllegalStateException(
                    "No se puede devolver: los ítems seleccionados ya tienen una recepción "
                    + "con factura de compra asociada. Solicite una nota crédito al proveedor.");
        }
        if ("REJECTED".equalsIgnoreCase(receipt.getStatus())) {
            throw new IllegalStateException("La recepcion ya esta rechazada.");
        }

        // Validar y computar cantidades por linea
        List<GoodsReceiptLine> updatedLines = new java.util.ArrayList<>();
        BigDecimal totalReturnedThis = BigDecimal.ZERO;
        for (com.sigcon.backend.invoices.purchase_orders.application.CreateGoodsReturnRequest.Line ln : request.getLines()) {
            if (ln.getQuantityReturned() == null
                    || ln.getQuantityReturned().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("La cantidad a devolver debe ser mayor a cero.");
            }
            GoodsReceiptLine grLine = receiptLineRepository.findById(ln.getGoodsReceiptLineId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "La linea de recepcion " + ln.getGoodsReceiptLineId() + " no fue encontrada."));
            if (grLine.getGoodsReceipt() == null
                    || !grLine.getGoodsReceipt().getId().equals(receipt.getId())) {
                throw new IllegalArgumentException(
                        "La linea " + grLine.getId() + " no pertenece a la recepcion " + receipt.getReceiptNumber());
            }
            BigDecimal received  = grLine.getQuantityReceived() != null ? grLine.getQuantityReceived() : BigDecimal.ZERO;
            BigDecimal already   = grLine.getQuantityReturned() != null ? grLine.getQuantityReturned() : BigDecimal.ZERO;
            BigDecimal available = received.subtract(already);
            if (ln.getQuantityReturned().compareTo(available) > 0) {
                throw new IllegalArgumentException(
                        "La cantidad a devolver supera la cantidad recibida disponible "
                        + "(linea " + grLine.getId() + ": disponible " + available + ").");
            }
            grLine.setQuantityReturned(already.add(ln.getQuantityReturned()));
            updatedLines.add(grLine);
            totalReturnedThis = totalReturnedThis.add(ln.getQuantityReturned());
        }

        // Persistir actualizacion de cantidades por linea
        for (GoodsReceiptLine line : updatedLines) {
            receiptLineRepository.save(line);
        }

        // Generar codigo de devolucion DV-{año}{6dig}
        long count = goodsReturnRepository != null ? goodsReturnRepository.count() : 0L;
        String returnNumber = String.format("DV-%d%06d", java.time.Year.now().getValue(), count + 1);

        com.sigcon.backend.invoices.purchase_orders.domain.model.GoodsReturn gr =
                com.sigcon.backend.invoices.purchase_orders.domain.model.GoodsReturn.builder()
                        .returnNumber(returnNumber)
                        .receipt(receipt)
                        .returnDate(request.getReturnDate())
                        .reason(request.getReason())
                        .createdBy(safeUserId())
                        .build();

        for (com.sigcon.backend.invoices.purchase_orders.application.CreateGoodsReturnRequest.Line ln : request.getLines()) {
            GoodsReceiptLine grLine = receiptLineRepository.findById(ln.getGoodsReceiptLineId()).orElseThrow();
            com.sigcon.backend.invoices.purchase_orders.domain.model.GoodsReturnLine returnLine =
                    com.sigcon.backend.invoices.purchase_orders.domain.model.GoodsReturnLine.builder()
                            .goodsReturn(gr)
                            .goodsReceiptLine(grLine)
                            .quantityReturned(ln.getQuantityReturned())
                            .notes(ln.getNotes())
                            .build();
            gr.getLines().add(returnLine);
        }

        gr = goodsReturnRepository.save(gr);

        // Recalcular status de la recepcion: si TODAS las lineas tienen
        // returned == received -> RETURNED (total). Si alguna tiene parcial,
        // PARTIALLY_RETURNED. Si todo sigue 0 -> RECEIVED (no deberia).
        boolean allFullyReturned = true;
        boolean anyReturned = false;
        for (GoodsReceiptLine line : receiptLineRepository.findAll()) {
            if (line.getGoodsReceipt() == null || !line.getGoodsReceipt().getId().equals(receipt.getId())) continue;
            BigDecimal received = line.getQuantityReceived() != null ? line.getQuantityReceived() : BigDecimal.ZERO;
            BigDecimal returned = line.getQuantityReturned() != null ? line.getQuantityReturned() : BigDecimal.ZERO;
            if (returned.compareTo(BigDecimal.ZERO) > 0) anyReturned = true;
            if (returned.compareTo(received) < 0) allFullyReturned = false;
        }
        String newStatus = allFullyReturned ? "RETURNED" : (anyReturned ? "PARTIALLY_RETURNED" : "RECEIVED");
        receipt.setStatus(newStatus);
        receiptRepository.save(receipt);

        // QA-BLOQUE-AY HU-AP-21 E1 (2026-05-06): tras devolver, la OC debe
        // recalcular su estado: si todas las cantidades devueltas dejaron al
        // OC con saldo pendiente otra vez, debe volver a PARTIALLY_RECEIVED o
        // APPROVED para permitir nuevas recepciones del proveedor.
        if (receipt.getPurchaseOrder() != null) {
            try {
                updatePurchaseOrderReceptionStatusAfterReturn(receipt.getPurchaseOrder());
            } catch (Exception ex) {
                log.warn("HU-AP-21 E1: no se pudo recalcular status OC {} tras devolucion: {}",
                        receipt.getPurchaseOrder().getOrderNumber(), ex.getMessage());
            }
        }

        auditPublisher.publishCreate(AuditModule.AP, "GoodsReturn", gr.getId(),
                "Devolucion " + returnNumber + " sobre recepcion " + receipt.getReceiptNumber()
                        + " - Cantidad total devuelta: " + totalReturnedThis + " - Motivo: " + request.getReason());

        log.info("HU-AP-21: devolucion {} creada sobre recepcion {} -> status {}",
                returnNumber, receipt.getReceiptNumber(), newStatus);

        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Devolucion registrada exitosamente"),
                Optional.of(buildReturnDTO(gr, receipt))));
    }

    /**
     * QA-BLOQUE-AY HU-AP-21 E1 (2026-05-06): variante de updatePurchaseOrderReceptionStatus
     * que se ejecuta tras una devolucion. Si la cantidad neta recibida (received-returned)
     * baja por debajo de lo pedido, la OC vuelve a PARTIALLY_RECEIVED. Si la devolucion
     * llevo el neto a 0, la OC vuelve a APPROVED.
     */
    private void updatePurchaseOrderReceptionStatusAfterReturn(PurchaseOrder order) {
        List<PurchaseOrderLine> lines = orderLineRepository.findByPurchaseOrderId(order.getId());
        if (lines == null || lines.isEmpty()) return;
        BigDecimal totalOrdered = BigDecimal.ZERO;
        BigDecimal totalNetReceived = BigDecimal.ZERO;
        for (PurchaseOrderLine line : lines) {
            BigDecimal qty = line.getQuantity() != null ? line.getQuantity() : BigDecimal.ZERO;
            totalOrdered = totalOrdered.add(qty);
            totalNetReceived = totalNetReceived.add(calculateTotalReceived(line.getId()));
        }
        String newStatus;
        if (totalNetReceived.signum() <= 0) {
            newStatus = "APPROVED";
        } else if (totalNetReceived.compareTo(totalOrdered) >= 0) {
            newStatus = "RECEIVED";
        } else {
            newStatus = "PARTIALLY_RECEIVED";
        }
        if (!newStatus.equals(order.getStatus())) {
            order.setStatus(newStatus);
            orderRepository.save(order);
            log.info("HU-AP-21 E1: OC {} status -> {} (neto recibido {} de {})",
                    order.getOrderNumber(), newStatus, totalNetReceived, totalOrdered);
        }
    }

    private Long safeUserId() {
        try { return userUtil.getUser().getId(); } catch (Exception e) { return null; }
    }

    private com.sigcon.backend.invoices.purchase_orders.application.GoodsReturnDTO buildReturnDTO(
            com.sigcon.backend.invoices.purchase_orders.domain.model.GoodsReturn gr,
            GoodsReceipt receipt) {
        return com.sigcon.backend.invoices.purchase_orders.application.GoodsReturnDTO.builder()
                .id(gr.getId())
                .returnNumber(gr.getReturnNumber())
                .receiptId(receipt.getId())
                .receiptNumber(receipt.getReceiptNumber())
                .returnDate(gr.getReturnDate())
                .reason(gr.getReason())
                .createdAt(gr.getCreatedAt())
                .lines(gr.getLines().stream().map(l ->
                        com.sigcon.backend.invoices.purchase_orders.application.GoodsReturnDTO.Line.builder()
                                .goodsReceiptLineId(l.getGoodsReceiptLine().getId())
                                .quantityReturned(l.getQuantityReturned())
                                .notes(l.getNotes())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }

    /**
     * QA-BLOQUE-AY HU-AP-19 E1 (2026-05-06): vincula una factura a MULTIPLES
     * recepciones de la misma OC. Patron tipico cuando el proveedor entrega
     * en varios despachos parciales pero emite una sola factura por todo.
     *
     * <ul>
     *   <li>Todas las recepciones deben pertenecer a la misma OC.</li>
     *   <li>Ninguna recepcion debe tener ya factura asociada (HU-AP-19 E4).</li>
     *   <li>Proveedor de la factura == proveedor de la OC (HU-AP-19 E2).</li>
     *   <li>Monto factura &gt; suma recibido = bloqueo (HU-AP-19 E3).</li>
     *   <li>Monto factura &lt; suma recibido = vincula con warning (HU-AP-19 E5).</li>
     * </ul>
     */
    @Transactional
    public ResponseEntity<?> linkToInvoiceMultiple(
            com.sigcon.backend.invoices.purchase_orders.application.LinkInvoiceMultipleRequest request) {

        if (request.getReceiptIds() == null || request.getReceiptIds().isEmpty()) {
            throw new IllegalArgumentException("Debe especificar al menos una recepcion");
        }

        // Cargar y validar todas las recepciones
        List<GoodsReceipt> receipts = new java.util.ArrayList<>();
        Long sharedOrderId = null;
        for (Long rid : request.getReceiptIds()) {
            GoodsReceipt r = receiptRepository.findById(rid)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "La recepcion " + rid + " no fue encontrada"));
            if (r.getInvoiceId() != null) {
                throw new IllegalStateException(
                        "La recepcion " + r.getReceiptNumber() + " ya tiene una factura vinculada (ID: "
                        + r.getInvoiceId() + ")");
            }
            if ("REJECTED".equalsIgnoreCase(r.getStatus())
                    || "RETURNED".equalsIgnoreCase(r.getStatus())) {
                throw new IllegalStateException(
                        "La recepcion " + r.getReceiptNumber() + " esta en estado "
                        + r.getStatus() + " y no puede vincularse a una factura.");
            }
            if (r.getPurchaseOrder() == null) {
                throw new IllegalArgumentException(
                        "La recepcion " + r.getReceiptNumber() + " no tiene OC asociada.");
            }
            Long orderId = r.getPurchaseOrder().getId();
            if (sharedOrderId == null) {
                sharedOrderId = orderId;
            } else if (!sharedOrderId.equals(orderId)) {
                throw new IllegalArgumentException(
                        "Todas las recepciones deben pertenecer a la misma orden de compra. "
                        + "OC primera: " + sharedOrderId + ", recepcion " + r.getReceiptNumber()
                        + " pertenece a OC " + orderId + ".");
            }
            receipts.add(r);
        }

        // Cargar factura
        Invoices invoice = invoiceRepository.findById(request.getInvoiceId())
                .orElseThrow(() -> new IllegalArgumentException("La factura no fue encontrada"));

        // HU-AP-19 E2: proveedor coincide
        Long invoiceThirdPartyId = invoice.getThirdParty() != null ? invoice.getThirdParty().getId() : null;
        Long orderThirdPartyId = receipts.get(0).getPurchaseOrder().getThirdParty() != null
                ? receipts.get(0).getPurchaseOrder().getThirdParty().getId() : null;
        if (invoiceThirdPartyId != null && orderThirdPartyId != null
                && !orderThirdPartyId.equals(invoiceThirdPartyId)) {
            throw new IllegalArgumentException(
                    "El proveedor de la factura no coincide con el de la recepción");
        }

        // HU-AP-19 E3/E5: comparar montos
        java.math.BigDecimal totalReceived = java.math.BigDecimal.ZERO;
        for (GoodsReceipt r : receipts) {
            if (r.getLines() == null) continue;
            for (var rl : r.getLines()) {
                java.math.BigDecimal qty = rl.getQuantityReceived();
                if (qty == null) continue;
                java.math.BigDecimal price = java.math.BigDecimal.ZERO;
                if (rl.getPurchaseOrderLine() != null
                        && rl.getPurchaseOrderLine().getUnitPrice() != null) {
                    price = rl.getPurchaseOrderLine().getUnitPrice();
                }
                totalReceived = totalReceived.add(qty.multiply(price));
            }
        }
        java.math.BigDecimal invoiceTotal = invoice.getTotalAmount() != null
                ? java.math.BigDecimal.valueOf(invoice.getTotalAmount())
                : java.math.BigDecimal.ZERO;

        String warning = null;
        if (totalReceived.signum() > 0) {
            int cmp = invoiceTotal.compareTo(totalReceived);
            if (cmp > 0) {
                throw new IllegalArgumentException(
                        "El monto de la factura ($" + invoiceTotal + ") supera el total recibido en las "
                        + receipts.size() + " recepcion(es) seleccionadas ($" + totalReceived + "). "
                        + "Verifique las cantidades o solicite una nota credito al proveedor.");
            } else if (cmp < 0) {
                java.math.BigDecimal diff = totalReceived.subtract(invoiceTotal);
                warning = "El monto facturado ($" + invoiceTotal + ") es inferior al total recibido ($"
                        + totalReceived + "). Diferencia: $" + diff
                        + ". Verifique si existe nota credito del proveedor pendiente de registrar.";
                log.warn("HU-AP-19 E5: factura {} multi-recepcion diff={}", invoice.getId(), diff);
            }
        }

        // Persistir vinculacion
        List<String> linkedNumbers = new java.util.ArrayList<>();
        for (GoodsReceipt r : receipts) {
            r.setInvoiceId(invoice.getId());
            receiptRepository.save(r);
            linkedNumbers.add(r.getReceiptNumber());
            auditPublisher.publishUpdate(AuditModule.AP, "GoodsReceipt", r.getId(),
                    "Recepcion " + r.getReceiptNumber() + " vinculada a factura "
                    + invoice.getResolutionInvoice() + " (multi-recepcion)"
                    + (warning != null ? " [WARN: " + warning + "]" : ""));
        }

        log.info("HU-AP-19 E1: factura {} vinculada a {} recepciones: {}",
                invoice.getResolutionInvoice(), receipts.size(), linkedNumbers);

        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("invoiceId", invoice.getId());
        payload.put("invoiceNumber", invoice.getResolutionInvoice());
        payload.put("linkedReceipts", linkedNumbers);
        payload.put("totalReceived", totalReceived);
        payload.put("invoiceTotal", invoiceTotal);
        if (warning != null) payload.put("warning", warning);

        String successMsg = warning != null
                ? ("Factura vinculada a " + receipts.size() + " recepciones con alerta: " + warning)
                : "Factura vinculada exitosamente a " + receipts.size() + " recepcion(es)";
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of(successMsg), Optional.of(payload)));
    }

    // ========================= Helpers privados =========================

    /**
     * Calcula la cantidad NETA recibida para una linea de OC (recibido menos
     * devuelto). QA-BLOQUE-AY HU-AP-21 E1 (2026-05-06): si la mercancia se
     * devuelve, la cantidad disponible para recepcionar de nuevo se restaura.
     *
     * @param purchaseOrderLineId ID de la linea de OC
     * @return cantidad neta recibida (total received - total returned)
     */
    private BigDecimal calculateTotalReceived(Long purchaseOrderLineId) {
        List<GoodsReceiptLine> receivedLines = receiptLineRepository
                .findByPurchaseOrderLineId(purchaseOrderLineId);
        BigDecimal totalReceived = receivedLines.stream()
                .map(l -> l.getQuantityReceived() != null ? l.getQuantityReceived() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalReturned = receivedLines.stream()
                .map(l -> l.getQuantityReturned() != null ? l.getQuantityReturned() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return totalReceived.subtract(totalReturned);
    }

    /**
     * Genera el numero consecutivo de recepcion con formato RC-{anio}{secuencia de 6 digitos}.
     *
     * @return numero de recepcion generado
     */
    private String generateReceiptNumber() {
        long count = receiptRepository.countAll() + 1;
        return String.format("RC-%d%06d", Year.now().getValue(), count);
    }

    /**
     * Convierte una entidad GoodsReceipt a su DTO de respuesta.
     *
     * @param receipt entidad a convertir
     * @return DTO con los datos de la recepcion
     */
    private GoodsReceiptDTO toDTO(GoodsReceipt receipt) {
        List<GoodsReceiptLineDTO> lineDTOs = List.of();
        if (receipt.getLines() != null) {
            lineDTOs = receipt.getLines().stream()
                    .map(this::toLineDTO)
                    .collect(Collectors.toList());
        }

        String poNumber = null;
        Long poId = null;
        try {
            if (receipt.getPurchaseOrder() != null) {
                poId = receipt.getPurchaseOrder().getId();
                poNumber = receipt.getPurchaseOrder().getOrderNumber();
            }
        } catch (Exception e) {
            // LazyInitializationException
        }

        return GoodsReceiptDTO.builder()
                .id(receipt.getId())
                .purchaseOrderId(poId)
                .purchaseOrderNumber(poNumber)
                .receiptNumber(receipt.getReceiptNumber())
                .receiptDate(receipt.getReceiptDate())
                .status(receipt.getStatus())
                .invoiceId(receipt.getInvoiceId())
                .notes(receipt.getNotes())
                .lines(lineDTOs)
                .build();
    }

    /**
     * Convierte una entidad GoodsReceiptLine a su DTO de respuesta.
     *
     * @param line entidad de linea a convertir
     * @return DTO con los datos de la linea de recepcion
     */
    private GoodsReceiptLineDTO toLineDTO(GoodsReceiptLine line) {
        String description = null;
        Long poLineId = null;
        try {
            if (line.getPurchaseOrderLine() != null) {
                poLineId = line.getPurchaseOrderLine().getId();
                description = line.getPurchaseOrderLine().getDescription();
            }
        } catch (Exception e) {
            // LazyInitializationException
        }

        return GoodsReceiptLineDTO.builder()
                .id(line.getId())
                .purchaseOrderLineId(poLineId)
                .description(description)
                .quantityReceived(line.getQuantityReceived())
                .build();
    }
}
