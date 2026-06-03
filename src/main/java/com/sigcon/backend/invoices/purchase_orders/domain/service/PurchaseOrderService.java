package com.sigcon.backend.invoices.purchase_orders.domain.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

import com.sigcon.backend.invoices.purchase_orders.application.ApprovePurchaseOrderRequest;
import com.sigcon.backend.invoices.purchase_orders.application.CreatePurchaseOrderLineRequest;
import com.sigcon.backend.invoices.purchase_orders.application.CreatePurchaseOrderRequest;
import com.sigcon.backend.invoices.purchase_orders.application.PurchaseOrderDTO;
import com.sigcon.backend.invoices.purchase_orders.application.PurchaseOrderLineDTO;
import com.sigcon.backend.invoices.purchase_orders.application.RejectPurchaseOrderRequest;
import com.sigcon.backend.invoices.purchase_orders.domain.model.PurchaseOrder;
import com.sigcon.backend.invoices.purchase_orders.domain.model.PurchaseOrderLine;
import com.sigcon.backend.invoices.purchase_orders.domain.repository.PurchaseOrderLineRepository;
import com.sigcon.backend.invoices.purchase_orders.domain.repository.PurchaseOrderRepository;
import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdParty;
import com.sigcon.backend.third_parties.third_parties.domain.repository.ThirdPartyRepository;
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
 * Servicio para la gestion de ordenes de compra en el modulo Cuentas por Pagar.
 * Implementa el ciclo de vida completo: creacion, edicion, envio a aprobacion,
 * aprobacion, rechazo y eliminacion de ordenes de compra.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PurchaseOrderService {

    private final PurchaseOrderRepository orderRepository;
    private final PurchaseOrderLineRepository lineRepository;
    private final ThirdPartyRepository thirdPartyRepository;
    private final UserUtil userUtil;
    private final AuditPublisher auditPublisher;

    /**
     * RF-15 (Notas Tecnicas CXP, 2026-06-02): secuencia por empresa para el
     * consecutivo de OC (reemplaza countAll()+1 global). Atomica (synchronized)
     * frente a concurrencia.
     */
    private final com.sigcon.backend.general.accounting.series.domain.service.VoucherSeriesService voucherSeriesService;

    /**
     * QA-BLOQUE-AY HU-AP-17 E1+E5+E6 (2026-05-05): inyeccion opcional para no
     * acoplar el modulo si Notifications no esta cargado. Se usa para
     * publicar PO_APPROVED / PO_REJECTED / PO_PENDING_APPROVAL.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.sigcon.backend.parametrization.notifications.domain.service.NotificationService notificationService;

    private final DataTableSpecificationBuilder<PurchaseOrder> specBuilder = new DataTableSpecificationBuilder<>();

    /**
     * Helper para publicar notificacion de cambio de estado de OC.
     * Falla silencioso (warn log) para no bloquear el flujo de negocio.
     */
    private void publishPoNotification(PurchaseOrder order, String eventKey, String title, String body,
            com.sigcon.backend.parametrization.notifications.domain.model.Notification.Severity severity) {
        if (notificationService == null) return;
        try {
            com.sigcon.backend.parametrization.notifications.application.PublishEventRequest req =
                com.sigcon.backend.parametrization.notifications.application.PublishEventRequest.builder()
                    .eventKey(eventKey)
                    .companyId(order.getCompanyId())
                    .sourceId(order.getId())
                    .sourceType("PurchaseOrder")
                    .severity(severity)
                    .title(title)
                    .body(body)
                    .actionUrl("/accounts-payable/purchase-orders?id=" + order.getId())
                    .build();
            notificationService.publishByRoleSubscription(req);
        } catch (Exception ex) {
            log.warn("HU-AP-17: no se pudo publicar notificacion {} para OC {}: {}",
                    eventKey, order.getOrderNumber(), ex.getMessage());
        }
    }

    /**
     * Crea una nueva orden de compra en estado DRAFT.
     * Genera automaticamente el numero consecutivo (OC-{anio}{secuencia de 6 digitos}).
     *
     * @param request datos de la orden y sus lineas
     * @return ResponseEntity con la orden creada
     * @throws IllegalArgumentException si el proveedor no existe
     */
    @Transactional
    public ResponseEntity<?> createOrder(CreatePurchaseOrderRequest request) {
        // 1. Validar proveedor
        ThirdParty thirdParty = thirdPartyRepository.findById(request.getThirdPartyId())
                .orElseThrow(() -> new IllegalArgumentException("El proveedor no fue encontrado"));

        // HU-AP-16 E2 (2026-04-28): bloquear OC con proveedor inactivo o
        // bloqueado. Antes el sistema permitia crear ordenes de compra a
        // proveedores en cualquier estado.
        if (thirdParty.getStatus() != null
                && !"ACTIVO".equalsIgnoreCase(thirdParty.getStatus().getName())) {
            throw new IllegalStateException(
                    "El proveedor no esta activo o no existe en el sistema.");
        }

        // 2. Generar numero de orden
        String orderNumber = generateOrderNumber();

        // 3. Construir la orden
        PurchaseOrder order = PurchaseOrder.builder()
                .orderNumber(orderNumber)
                .thirdParty(thirdParty)
                .orderDate(request.getOrderDate())
                .deliveryDate(request.getDeliveryDate())
                .status("DRAFT")
                .notes(request.getNotes())
                .createdBy(userUtil.getUser().getId())
                .build();

        // 4. Crear lineas y calcular total. HU-AP-15 E3 (Bloque AR): rechaza
        // cantidades <= 0 o precios <= 0 con mensaje literal del Excel.
        validateOrderLines(request.getLines());

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (CreatePurchaseOrderLineRequest lineReq : request.getLines()) {
            BigDecimal totalLine = lineReq.getQuantity().multiply(lineReq.getUnitPrice());
            PurchaseOrderLine line = PurchaseOrderLine.builder()
                    .purchaseOrder(order)
                    .description(lineReq.getDescription())
                    .quantity(lineReq.getQuantity())
                    .unitPrice(lineReq.getUnitPrice())
                    .totalLine(totalLine)
                    .build();
            order.getLines().add(line);
            totalAmount = totalAmount.add(totalLine);
        }
        order.setTotalAmount(totalAmount);

        order = orderRepository.save(order);
        auditPublisher.publishCreate(AuditModule.AP, "PurchaseOrder", order.getId(), "PurchaseOrder creado id=" + order.getId());
        log.info("Orden de compra {} creada para proveedor {}", orderNumber, thirdParty.getId());

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Orden de compra creada exitosamente"), Optional.of(toDTO(order))));
    }

    /**
     * Consulta ordenes de compra con paginacion y filtros DataTable.
     *
     * @param request parametros de busqueda y paginacion
     * @return listado paginado de ordenes de compra
     */
    public ResponseEntity<?> getOrders(DataTableRequest request) {
        int start = Math.max(0, request.getStart());
        int length = request.getLength();
        int safeLength = length <= 0 ? 20 : Math.min(length, 100);
        int page = start / safeLength;

        Pageable pageable = length == -1
                ? Pageable.unpaged()
                : PageRequest.of(page, safeLength);

        Specification<PurchaseOrder> spec = specBuilder.build(request);
        Page<PurchaseOrderDTO> data = orderRepository.findAll(spec, pageable).map(this::toDTO);

        return ResponseEntity.ok(DataTableResponse.from(data, request.getDraw()));
    }

    /**
     * Obtiene una orden de compra por su identificador, incluyendo sus lineas de detalle.
     *
     * @param id identificador de la orden
     * @return ResponseEntity con la orden encontrada
     * @throws IllegalArgumentException si la orden no existe
     */
    public ResponseEntity<?> getOrderById(Long id) {
        PurchaseOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La orden de compra no fue encontrada"));
        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Orden de compra encontrada"), Optional.of(toDTO(order))));
    }

    /**
     * Actualiza una orden de compra existente.
     * Solo permite actualizar ordenes en estado DRAFT.
     * Recalcula el monto total con las nuevas lineas.
     *
     * @param id      identificador de la orden
     * @param request datos actualizados
     * @return ResponseEntity con la orden actualizada
     * @throws IllegalArgumentException si la orden no existe o el proveedor no existe
     * @throws IllegalStateException    si la orden no esta en estado DRAFT
     */
    @Transactional
    public ResponseEntity<?> updateOrder(Long id, CreatePurchaseOrderRequest request) {
        PurchaseOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La orden de compra no fue encontrada"));

        // HU-AP-17 E2 (Bloque AT): OC en estado DRAFT o REJECTED puede editarse.
        // QA reporto que tras rechazo, el solicitante debe poder corregir y
        // re-enviar a aprobacion. Otros estados (PENDING/APPROVED/CLOSED) son
        // inmutables.
        if (!"DRAFT".equals(order.getStatus()) && !"REJECTED".equals(order.getStatus())) {
            throw new IllegalStateException(
                "Solo se pueden modificar ordenes en estado BORRADOR o RECHAZADA. "
                + "Estado actual: " + order.getStatus());
        }
        // Si se edita una rechazada, vuelve a DRAFT para que pueda re-enviarse a aprobacion
        boolean wasRejected = "REJECTED".equals(order.getStatus());
        if (wasRejected) {
            order.setStatus("DRAFT");
            order.setRejectionReason(null);
            log.info("HU-AP-17 E2 (Bloque AT): OC {} editada tras rechazo, vuelve a DRAFT", order.getId());
        }

        // Actualizar proveedor si cambio
        if (request.getThirdPartyId() != null) {
            ThirdParty thirdParty = thirdPartyRepository.findById(request.getThirdPartyId())
                    .orElseThrow(() -> new IllegalArgumentException("El proveedor no fue encontrado"));
            order.setThirdParty(thirdParty);
        }

        if (request.getOrderDate() != null) {
            order.setOrderDate(request.getOrderDate());
        }
        order.setDeliveryDate(request.getDeliveryDate());
        if (request.getNotes() != null) {
            order.setNotes(request.getNotes());
        }

        // Reemplazar lineas
        if (request.getLines() != null && !request.getLines().isEmpty()) {
            // HU-AP-15 E3 (Bloque AR): validacion tambien en update.
            validateOrderLines(request.getLines());
            order.getLines().clear();
            BigDecimal totalAmount = BigDecimal.ZERO;
            for (CreatePurchaseOrderLineRequest lineReq : request.getLines()) {
                BigDecimal totalLine = lineReq.getQuantity().multiply(lineReq.getUnitPrice());
                PurchaseOrderLine line = PurchaseOrderLine.builder()
                        .purchaseOrder(order)
                        .description(lineReq.getDescription())
                        .quantity(lineReq.getQuantity())
                        .unitPrice(lineReq.getUnitPrice())
                        .totalLine(totalLine)
                        .build();
                order.getLines().add(line);
                totalAmount = totalAmount.add(totalLine);
            }
            order.setTotalAmount(totalAmount);
        }

        order = orderRepository.save(order);
        auditPublisher.publishUpdate(AuditModule.AP, "PurchaseOrder", order.getId(), "PurchaseOrder actualizado id=" + order.getId());
        log.info("Orden de compra {} actualizada", order.getOrderNumber());

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Orden de compra actualizada exitosamente"), Optional.of(toDTO(order))));
    }

    /**
     * Envia una orden de compra para aprobacion.
     * Cambia el estado de DRAFT a PENDING.
     *
     * @param id identificador de la orden
     * @return ResponseEntity con la orden actualizada
     * @throws IllegalArgumentException si la orden no existe
     * @throws IllegalStateException    si la orden no esta en estado DRAFT
     */
    @Transactional
    public ResponseEntity<?> submitForApproval(Long id) {
        PurchaseOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La orden de compra no fue encontrada"));

        if (!"DRAFT".equals(order.getStatus())) {
            throw new IllegalStateException("Solo se pueden enviar a aprobacion ordenes en estado borrador (DRAFT)");
        }

        order.setStatus("PENDING");
        order = orderRepository.save(order);
        log.info("Orden de compra {} enviada a aprobacion", order.getOrderNumber());

        // QA-BLOQUE-AY HU-AP-17 E5: notificar a roles aprobadores (ADMIN/CONTADOR)
        // que hay una OC esperando decision.
        publishPoNotification(order, "PO_PENDING_APPROVAL",
                "Orden de compra pendiente de aprobacion",
                "OC " + order.getOrderNumber() + " del proveedor "
                        + (order.getThirdParty() != null && order.getThirdParty().getBusinessName() != null
                                ? order.getThirdParty().getBusinessName() : "(sin nombre)")
                        + " por $" + order.getTotalAmount() + " esta pendiente de aprobacion.",
                com.sigcon.backend.parametrization.notifications.domain.model.Notification.Severity.INFO);

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Orden de compra enviada a aprobacion"), Optional.of(toDTO(order))));
    }

    /**
     * Aprueba una orden de compra pendiente.
     * Cambia el estado de PENDING a APPROVED y registra quien y cuando aprobo.
     *
     * @param id      identificador de la orden
     * @param request datos opcionales de aprobacion
     * @return ResponseEntity con la orden aprobada
     * @throws IllegalArgumentException si la orden no existe
     * @throws IllegalStateException    si la orden no esta en estado PENDING
     */
    @Transactional
    public ResponseEntity<?> approveOrder(Long id, ApprovePurchaseOrderRequest request) {
        PurchaseOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La orden de compra no fue encontrada"));

        if (!"PENDING".equals(order.getStatus())) {
            throw new IllegalStateException("Solo se pueden aprobar ordenes en estado pendiente (PENDING)");
        }

        // AP-RF-17 v2.0 (Notas Tecnicas CXP, 2026-06-02): nivel de aprobacion por monto,
        // mapeado a ROLES REALES del sistema (decision Opcion B del lider):
        //   > $100M COP: requiere CONTADOR, TESORERO o ADMIN_EMPRESA (supervisores funcionales).
        //   > $500M COP: requiere ADMIN_EMPRESA (nivel administrador/director).
        //   ADMIN_EMPRESA (y los roles de plataforma) tienen bypass completo.
        // Antes se chequeaban roles "SUPERVISOR"/"DIRECTOR" que NO existen en el sistema, por
        // lo que ni CONTADOR/TESORERO ni siquiera ADMIN_EMPRESA podian aprobar OC de alto monto.
        java.math.BigDecimal THRESHOLD_SUPERVISOR = new java.math.BigDecimal("100000000"); // 100M COP
        java.math.BigDecimal THRESHOLD_DIRECTOR = new java.math.BigDecimal("500000000");   // 500M COP

        java.util.Set<String> roleNames = userUtil.getUser().getRoles().stream()
                .map(r -> r.getName() == null ? "" : r.getName().toUpperCase())
                .collect(java.util.stream.Collectors.toSet());

        // Nivel director: ADMIN_EMPRESA + roles de plataforma/legacy admin (bypass total).
        boolean isDirectorLevel = roleNames.contains("ADMIN_EMPRESA")
                || roleNames.contains("ADMIN")
                || roleNames.contains("SUPERADMIN")
                || roleNames.contains("PLATFORM_ADMIN");
        // Nivel supervisor: CONTADOR, TESORERO o cualquier nivel director.
        boolean isSupervisorLevel = isDirectorLevel
                || roleNames.contains("CONTADOR")
                || roleNames.contains("TESORERO");

        if (order.getTotalAmount() != null) {
            if (order.getTotalAmount().compareTo(THRESHOLD_DIRECTOR) > 0 && !isDirectorLevel) {
                throw new IllegalStateException(
                        "El monto de la orden supera $500M COP. Se requiere aprobacion de nivel ADMINISTRADOR.");
            }
            if (order.getTotalAmount().compareTo(THRESHOLD_SUPERVISOR) > 0 && !isSupervisorLevel) {
                throw new IllegalStateException(
                        "El monto de la orden supera $100M COP. Se requiere aprobacion de nivel CONTADOR/TESORERO o superior.");
            }
        }

        order.setStatus("APPROVED");
        order.setApprovedBy(userUtil.getUser().getId());
        order.setApprovedAt(LocalDateTime.now());
        if (request != null && request.getNotes() != null) {
            order.setNotes(order.getNotes() != null
                    ? order.getNotes() + " | Aprobacion: " + request.getNotes()
                    : "Aprobacion: " + request.getNotes());
        }

        order = orderRepository.save(order);
        log.info("Orden de compra {} aprobada por usuario {}", order.getOrderNumber(), order.getApprovedBy());
        // HU-AP-18 E1 (2026-04-28): registrar la aprobacion en auditoria.
        auditPublisher.publishUpdate(AuditModule.AP, "PurchaseOrder", order.getId(),
                "OC " + order.getOrderNumber() + " APROBADA por user " + order.getApprovedBy()
                        + " (monto $" + order.getTotalAmount() + ")");

        // QA-BLOQUE-AY HU-AP-17 E1: notificar al solicitante que su OC fue aprobada.
        publishPoNotification(order, "PO_APPROVED",
                "Orden de compra aprobada",
                "Su OC " + order.getOrderNumber() + " del proveedor "
                        + (order.getThirdParty() != null && order.getThirdParty().getBusinessName() != null
                                ? order.getThirdParty().getBusinessName() : "(sin nombre)")
                        + " por $" + order.getTotalAmount() + " fue aprobada.",
                com.sigcon.backend.parametrization.notifications.domain.model.Notification.Severity.INFO);

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Orden de compra aprobada exitosamente"), Optional.of(toDTO(order))));
    }

    /**
     * Rechaza una orden de compra pendiente.
     * Cambia el estado de PENDING a REJECTED y registra la razon del rechazo.
     *
     * @param id      identificador de la orden
     * @param request datos del rechazo (razon obligatoria)
     * @return ResponseEntity con la orden rechazada
     * @throws IllegalArgumentException si la orden no existe
     * @throws IllegalStateException    si la orden no esta en estado PENDING
     */
    @Transactional
    public ResponseEntity<?> rejectOrder(Long id, RejectPurchaseOrderRequest request) {
        PurchaseOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La orden de compra no fue encontrada"));

        if (!"PENDING".equals(order.getStatus())) {
            throw new IllegalStateException("Solo se pueden rechazar ordenes en estado pendiente (PENDING)");
        }

        order.setStatus("REJECTED");
        order.setRejectionReason(request.getRejectionReason());

        order = orderRepository.save(order);
        log.info("Orden de compra {} rechazada: {}", order.getOrderNumber(), request.getRejectionReason());
        // HU-AP-18 E2 (2026-04-28): registrar el rechazo en auditoria con motivo.
        auditPublisher.publishUpdate(AuditModule.AP, "PurchaseOrder", order.getId(),
                "OC " + order.getOrderNumber() + " RECHAZADA - motivo: " + request.getRejectionReason());

        // QA-BLOQUE-AY HU-AP-17 E6: notificar al solicitante con motivo del rechazo.
        publishPoNotification(order, "PO_REJECTED",
                "Orden de compra rechazada",
                "Su OC " + order.getOrderNumber() + " del proveedor "
                        + (order.getThirdParty() != null && order.getThirdParty().getBusinessName() != null
                                ? order.getThirdParty().getBusinessName() : "(sin nombre)")
                        + " fue rechazada. Motivo: " + request.getRejectionReason(),
                com.sigcon.backend.parametrization.notifications.domain.model.Notification.Severity.WARNING);

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Orden de compra rechazada"), Optional.of(toDTO(order))));
    }

    /**
     * Elimina logicamente una orden de compra.
     * Solo permite eliminar ordenes en estado DRAFT.
     *
     * @param id identificador de la orden
     * @return ResponseEntity con mensaje de exito
     * @throws IllegalArgumentException si la orden no existe
     * @throws IllegalStateException    si la orden no esta en estado DRAFT
     */
    @Transactional
    public ResponseEntity<?> deleteOrder(Long id) {
        PurchaseOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La orden de compra no fue encontrada"));

        if (!"DRAFT".equals(order.getStatus())) {
            throw new IllegalStateException("Solo se pueden eliminar ordenes en estado borrador (DRAFT)");
        }

        orderRepository.deleteById(id);
        log.info("Orden de compra {} eliminada", order.getOrderNumber());

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Orden de compra eliminada exitosamente"), Optional.empty()));
    }

    // ========================= Helpers privados =========================

    /**
     * Genera el numero consecutivo de orden de compra con formato OC-{anio}{secuencia de 6 digitos}.
     *
     * @return numero de orden generado
     */
    private String generateOrderNumber() {
        // RF-15 (Notas Tecnicas CXP, 2026-06-02): consecutivo por EMPRESA via
        // secuencia atomica (VoucherSeriesService), en vez de countAll()+1 global
        // que colisionaba en concurrencia y mezclaba empresas. Self-heal: sincroniza
        // la serie al MAX real de la empresa antes de consumir (cubre seeds que
        // insertaron OCs sin pasar por la serie).
        Long companyId = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
        if (companyId != null) {
            long maxExisting = orderRepository.findMaxOrderSequence(companyId);
            voucherSeriesService.syncToAtLeast("OC", maxExisting);
        }
        long seq = voucherSeriesService.consumeNext("OC");
        return String.format("OC-%d%06d", Year.now().getValue(), seq);
    }

    /**
     * Convierte una entidad PurchaseOrder a su DTO de respuesta.
     *
     * @param order entidad a convertir
     * @return DTO con los datos de la orden
     */
    private PurchaseOrderDTO toDTO(PurchaseOrder order) {
        List<PurchaseOrderLineDTO> lineDTOs = List.of();
        if (order.getLines() != null) {
            lineDTOs = order.getLines().stream()
                    .map(this::toLineDTO)
                    .collect(Collectors.toList());
        }

        String thirdPartyName = null;
        try {
            if (order.getThirdParty() != null) {
                thirdPartyName = order.getThirdParty().getBusinessName();
            }
        } catch (Exception e) {
            // LazyInitializationException - solo registrar ID
        }

        return PurchaseOrderDTO.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .thirdPartyId(order.getThirdParty() != null ? order.getThirdParty().getId() : null)
                .thirdPartyName(thirdPartyName)
                .orderDate(order.getOrderDate())
                .deliveryDate(order.getDeliveryDate())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .notes(order.getNotes())
                .lines(lineDTOs)
                .build();
    }

    /**
     * Convierte una entidad PurchaseOrderLine a su DTO de respuesta.
     *
     * @param line entidad de linea a convertir
     * @return DTO con los datos de la linea
     */
    private PurchaseOrderLineDTO toLineDTO(PurchaseOrderLine line) {
        return PurchaseOrderLineDTO.builder()
                .id(line.getId())
                .description(line.getDescription())
                .quantity(line.getQuantity())
                .unitPrice(line.getUnitPrice())
                .totalLine(line.getTotalLine())
                .build();
    }

    /**
     * HU-AP-15 E3 (Bloque AR): bloquea creacion/edicion de OC con cantidades
     * negativas o cero, o precios unitarios cero. Mensaje literal HU.
     */
    private void validateOrderLines(java.util.List<CreatePurchaseOrderLineRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        int idx = 1;
        for (CreatePurchaseOrderLineRequest l : lines) {
            if (l.getQuantity() == null || l.getQuantity().signum() <= 0) {
                throw new IllegalArgumentException(
                        "Linea " + idx + ": la cantidad debe ser mayor a cero. "
                        + "No se puede generar una orden de compra con cantidades invalidas.");
            }
            if (l.getUnitPrice() == null || l.getUnitPrice().signum() <= 0) {
                throw new IllegalArgumentException(
                        "Linea " + idx + ": el precio unitario debe ser mayor a cero. "
                        + "No se puede generar una orden de compra con precios invalidos.");
            }
            idx++;
        }
    }
}
