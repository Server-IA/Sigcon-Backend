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
import com.sigcon.backend.invoices.purchase_orders.domain.repository.GoodsReceiptLineRepository;
import com.sigcon.backend.invoices.purchase_orders.domain.repository.GoodsReceiptRepository;
import com.sigcon.backend.invoices.purchase_orders.domain.repository.PurchaseOrderLineRepository;
import com.sigcon.backend.invoices.purchase_orders.domain.repository.PurchaseOrderRepository;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.utils.SuccessRespondJson;
import com.sigcon.backend.utils.UserUtil;

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

        if (!"APPROVED".equals(order.getStatus())) {
            throw new IllegalStateException("Solo se pueden crear recepciones para ordenes aprobadas (APPROVED)");
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
        log.info("Recepcion {} creada para OC {}", receiptNumber, order.getOrderNumber());

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Recepcion registrada exitosamente"), Optional.of(toDTO(receipt))));
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

        if (receipt.getInvoiceId() != null) {
            throw new IllegalStateException("La recepcion ya tiene una factura vinculada (ID: " + receipt.getInvoiceId() + ")");
        }

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
                        "Proveedor de factura y recepción no coinciden.");
            }
        }

        receipt.setInvoiceId(invoice.getId());
        receipt = receiptRepository.save(receipt);
        log.info("Recepcion {} vinculada a factura {}", receipt.getReceiptNumber(), invoice.getId());

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Factura vinculada exitosamente a la recepcion"), Optional.of(toDTO(receipt))));
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

        if (receipt.getInvoiceId() != null) {
            throw new IllegalStateException(
                    "No se puede rechazar una recepcion vinculada a factura (ID: "
                            + receipt.getInvoiceId() + "). Desvinculela primero.");
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

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Recepcion rechazada exitosamente"), Optional.of(toDTO(receipt))));
    }

    // ========================= Helpers privados =========================

    /**
     * Calcula la cantidad total ya recibida para una linea de orden de compra.
     *
     * @param purchaseOrderLineId ID de la linea de OC
     * @return cantidad total recibida
     */
    private BigDecimal calculateTotalReceived(Long purchaseOrderLineId) {
        List<GoodsReceiptLine> receivedLines = receiptLineRepository
                .findByPurchaseOrderLineId(purchaseOrderLineId);
        return receivedLines.stream()
                .map(GoodsReceiptLine::getQuantityReceived)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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
