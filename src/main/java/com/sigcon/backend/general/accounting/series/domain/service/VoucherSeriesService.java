package com.sigcon.backend.general.accounting.series.domain.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;
import com.sigcon.backend.general.accounting.series.application.CreateVoucherSeriesRequest;
import com.sigcon.backend.general.accounting.series.application.VoucherSeriesConfigDTO;
import com.sigcon.backend.general.accounting.series.domain.model.VoucherSeriesConfig;
import com.sigcon.backend.general.accounting.series.domain.repository.VoucherSeriesConfigRepository;
import com.sigcon.backend.utils.SuccessRespondJson;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * HU-CG-03A E3/E5: servicio de configuracion de series de consecutivos por
 * tipo de comprobante. Permite crear, actualizar, listar y consumir
 * consecutivos. Lanza excepcion cuando el rango se agota o el sistema intenta
 * consumir un numero fuera de rango.
 *
 * <p>Uso desde JournalEntryService.assignNextNumber("JE") para reemplazar el
 * findMax(entryNumber)+1 que se usaba antes.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherSeriesService {

    private final VoucherSeriesConfigRepository repository;
    private final AuditPublisher auditPublisher;

    /** HU-CG-03A E3: lista todas las series configuradas en la empresa. */
    public ResponseEntity<?> findAll() {
        List<VoucherSeriesConfigDTO> list = repository.findAllByDeletedAtIsNullOrderByVoucherTypeAsc()
                .stream().map(this::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Series obtenidas"), Optional.of(list)));
    }

    /** HU-CG-03A E3: detalle de una serie. */
    public ResponseEntity<?> findById(Long id) {
        VoucherSeriesConfig s = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Serie no encontrada: " + id));
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Serie obtenida"), Optional.of(toDto(s))));
    }

    /** HU-CG-03A E3: crea una nueva serie de consecutivos. */
    @Transactional
    public ResponseEntity<?> create(CreateVoucherSeriesRequest req) {
        if (repository.existsByVoucherTypeAndDeletedAtIsNull(req.getVoucherType())) {
            throw new IllegalArgumentException(
                    "Ya existe una serie activa para el tipo " + req.getVoucherType()
                    + ". Edite la serie existente o use otro tipo.");
        }
        if (req.getEndNumber() < req.getStartNumber()) {
            throw new IllegalArgumentException(
                    "El numero final debe ser mayor o igual al inicial.");
        }
        long current = req.getCurrentNumber() != null ? req.getCurrentNumber() : 0L;
        if (current > 0 && current < req.getStartNumber() - 1) {
            throw new IllegalArgumentException(
                    "El numero actual debe ser >= " + (req.getStartNumber() - 1) + " (inicio - 1).");
        }
        if (current > req.getEndNumber()) {
            throw new IllegalArgumentException(
                    "El numero actual no puede superar el numero final del rango.");
        }

        VoucherSeriesConfig s = VoucherSeriesConfig.builder()
                .voucherType(req.getVoucherType().toUpperCase())
                .prefix(req.getPrefix().toUpperCase())
                .startNumber(req.getStartNumber())
                .endNumber(req.getEndNumber())
                .currentNumber(current)
                .alertThresholdPct(req.getAlertThresholdPct())
                .description(req.getDescription())
                .status("ACTIVE")
                .build();
        s = repository.save(s);
        auditPublisher.publishCreate(AuditModule.CG, "VoucherSeriesConfig", s.getId(),
                "Serie de consecutivos creada tipo=" + s.getVoucherType()
                        + " rango=" + s.getStartNumber() + ".." + s.getEndNumber());
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Serie creada correctamente"), Optional.of(toDto(s))));
    }

    /** HU-CG-03A E3: actualiza una serie existente. */
    @Transactional
    public ResponseEntity<?> update(Long id, CreateVoucherSeriesRequest req) {
        VoucherSeriesConfig s = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Serie no encontrada: " + id));
        if (repository.existsByVoucherTypeAndIdNotAndDeletedAtIsNull(req.getVoucherType(), id)) {
            throw new IllegalArgumentException(
                    "Ya existe otra serie activa con el mismo tipo " + req.getVoucherType() + ".");
        }
        if (req.getEndNumber() < req.getStartNumber()) {
            throw new IllegalArgumentException("El numero final debe ser mayor o igual al inicial.");
        }
        long newCurrent = req.getCurrentNumber() != null ? req.getCurrentNumber() : s.getCurrentNumber();
        if (newCurrent < s.getCurrentNumber()) {
            throw new IllegalArgumentException(
                    "No se puede retroceder el numero actual (era " + s.getCurrentNumber()
                    + ", se intento " + newCurrent + "). Cree una serie nueva si necesita reiniciar.");
        }
        if (newCurrent > req.getEndNumber()) {
            throw new IllegalArgumentException(
                    "El numero actual no puede superar el numero final del rango.");
        }

        s.setVoucherType(req.getVoucherType().toUpperCase());
        s.setPrefix(req.getPrefix().toUpperCase());
        s.setStartNumber(req.getStartNumber());
        s.setEndNumber(req.getEndNumber());
        s.setCurrentNumber(newCurrent);
        s.setAlertThresholdPct(req.getAlertThresholdPct());
        s.setDescription(req.getDescription());
        // Si el rango se amplio, EXHAUSTED -> ACTIVE
        if ("EXHAUSTED".equals(s.getStatus()) && newCurrent < req.getEndNumber()) {
            s.setStatus("ACTIVE");
        }
        s = repository.save(s);
        auditPublisher.publishUpdate(AuditModule.CG, "VoucherSeriesConfig", s.getId(),
                "Serie actualizada tipo=" + s.getVoucherType()
                        + " rango=" + s.getStartNumber() + ".." + s.getEndNumber()
                        + " current=" + s.getCurrentNumber());
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Serie actualizada correctamente"), Optional.of(toDto(s))));
    }

    /** HU-CG-03A E3: soft delete de la serie. */
    @Transactional
    public ResponseEntity<?> delete(Long id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("Serie no encontrada: " + id);
        }
        repository.deleteById(id);
        auditPublisher.publishDelete(AuditModule.CG, "VoucherSeriesConfig", id,
                "Serie de consecutivos eliminada id=" + id);
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Serie eliminada correctamente"), Optional.empty()));
    }

    /**
     * HU-CG-03A E5: consume el siguiente numero de la serie del tipo indicado
     * (default 'JE' si no hay configuracion). Retorna el numero asignado y
     * actualiza current_number. Si el rango se agota, marca EXHAUSTED y lanza
     * excepcion con mensaje claro para que la UI muestre alerta al admin.
     *
     * <p>Sincronizado a nivel JVM con un lock por (companyId, voucherType) para
     * evitar race conditions en concurrencia. Para un sistema con N nodos se
     * requeriria SELECT FOR UPDATE en BD; en el alcance actual single-node es
     * suficiente.</p>
     */
    @Transactional
    public synchronized long consumeNext(String voucherType) {
        String type = voucherType != null ? voucherType.toUpperCase() : "JE";
        VoucherSeriesConfig s = repository.findByVoucherTypeAndDeletedAtIsNull(type)
                .orElse(null);
        if (s == null) {
            // Auto-provision lazy: si la empresa nunca creo serie del tipo, generamos
            // una default 1..999999 con prefix=type.
            s = VoucherSeriesConfig.builder()
                    .voucherType(type).prefix(type)
                    .startNumber(1L).endNumber(999999L).currentNumber(0L)
                    .alertThresholdPct(80)
                    .description("Auto-provisionada al primer consumo")
                    .status("ACTIVE")
                    .build();
            s = repository.save(s);
            log.info("Serie auto-provisionada para tipo {}", type);
        }
        if ("EXHAUSTED".equals(s.getStatus()) || "INACTIVE".equals(s.getStatus())) {
            throw new IllegalStateException(
                    "La serie de consecutivos " + type + " esta " + s.getStatus()
                    + ". Amplie el rango o active una serie nueva antes de generar mas comprobantes.");
        }
        long next = Math.max(s.getCurrentNumber() + 1, s.getStartNumber());
        if (next > s.getEndNumber()) {
            s.setStatus("EXHAUSTED");
            repository.save(s);
            throw new IllegalStateException(
                    "El rango de la serie " + type + " (" + s.getStartNumber() + ".." + s.getEndNumber()
                    + ") se ha agotado. Amplie el rango desde Parametrizacion para continuar emitiendo "
                    + "comprobantes.");
        }
        s.setCurrentNumber(next);
        repository.save(s);
        // Alerta al admin si pasa el umbral configurado
        long range = s.getEndNumber() - s.getStartNumber() + 1;
        long used = next - s.getStartNumber() + 1;
        int pct = (int) ((used * 100) / range);
        if (pct >= s.getAlertThresholdPct()) {
            log.warn("Serie {} en {}% de uso ({}/{}). Considere ampliar el rango.",
                     type, pct, used, range);
            auditPublisher.publish(
                    com.sigcon.backend.audit.domain.model.enums.AuditAction.UPDATE,
                    AuditModule.CG,
                    com.sigcon.backend.audit.domain.model.enums.AuditSeverity.HIGH,
                    "VoucherSeriesConfig", s.getId(),
                    "Serie " + type + " supero el umbral de alerta (" + pct
                            + "% >= " + s.getAlertThresholdPct() + "%)",
                    null, null, null);
        }
        return next;
    }

    /**
     * Bug ACT-RF-01 (2026-06-01): adelanta el contador de la serie {@code type}
     * a AL MENOS {@code min} (nunca lo retrocede). Self-heal contra la
     * DESINCRONIZACION entre la serie y el MAX real de consecutivos: cuando seeds
     * u otros flujos insertan comprobantes directamente sin consumir la serie, el
     * contador queda atrasado y {@link #consumeNext} devolveria un numero que YA
     * existe -> "duplicate key uk_journal_entries_company_fy_num". El llamador
     * (JournalEntryService) calcula {@code min} = MAX(entry_number) del tenant
     * para el anio fiscal y sincroniza ANTES de consumir.
     *
     * <p>{@code synchronized} sobre la misma instancia que {@link #consumeNext},
     * de modo que la secuencia sync->consume del mismo hilo es consistente y los
     * hilos concurrentes se serializan (single-node).</p>
     */
    @Transactional
    public synchronized void syncToAtLeast(String voucherType, long min) {
        String type = voucherType != null ? voucherType.toUpperCase() : "JE";
        VoucherSeriesConfig s = repository.findByVoucherTypeAndDeletedAtIsNull(type).orElse(null);
        if (s == null) {
            // RF-15/18 (Notas Tecnicas CXP, 2026-06-02): si la serie AUN NO existe
            // (caso de tipos que no se siembran como OC/RC/DV en su primer uso) y ya
            // hay consecutivos previos (min>0, p.ej. seeds), auto-provisionamos la
            // serie arrancando en `min`, para que el primer consumeNext entregue
            // min+1 y NO reutilice un consecutivo ya usado. Si min<=0 no hacemos nada:
            // consumeNext la creara en 0 y entregara 1 (BD limpia, sin colisiones).
            if (min <= 0) return;
            s = VoucherSeriesConfig.builder()
                    .voucherType(type).prefix(type)
                    .startNumber(1L).endNumber(999999L).currentNumber(min)
                    .alertThresholdPct(80)
                    .description("Auto-provisionada por sincronizacion al MAX existente")
                    .status("ACTIVE")
                    .build();
            repository.save(s);
            log.info("Serie {} auto-provisionada y sincronizada al MAX existente ({})", type, min);
            return;
        }
        if (s.getCurrentNumber() < min) {
            log.info("Serie {} sincronizada: current_number {} -> {} (estaba atrasada respecto al MAX real)",
                    type, s.getCurrentNumber(), min);
            s.setCurrentNumber(min);
            repository.save(s);
        }
    }

    /** Calcula el porcentaje usado y el flag de alerta para enriquecer el DTO. */
    private VoucherSeriesConfigDTO toDto(VoucherSeriesConfig s) {
        long range = s.getEndNumber() - s.getStartNumber() + 1;
        long used = Math.max(0L, s.getCurrentNumber() - s.getStartNumber() + 1);
        int pct = range > 0 ? (int) ((used * 100) / range) : 0;
        boolean alert = pct >= s.getAlertThresholdPct();
        return VoucherSeriesConfigDTO.builder()
                .id(s.getId())
                .voucherType(s.getVoucherType())
                .prefix(s.getPrefix())
                .startNumber(s.getStartNumber())
                .endNumber(s.getEndNumber())
                .currentNumber(s.getCurrentNumber())
                .alertThresholdPct(s.getAlertThresholdPct())
                .description(s.getDescription())
                .status(s.getStatus())
                .usedPct(pct)
                .alert(alert)
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }
}
