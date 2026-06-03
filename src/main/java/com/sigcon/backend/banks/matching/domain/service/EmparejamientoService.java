package com.sigcon.backend.banks.matching.domain.service;

import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;
import com.sigcon.backend.banks.bankaccounts.domain.model.BankAccount;
import com.sigcon.backend.banks.bankaccounts.domain.repository.BankAccountRepository;
import com.sigcon.backend.banks.financialmovements.domain.model.FinancialMovement;
import com.sigcon.backend.banks.financialmovements.domain.repository.FinancialMovementRepository;
import com.sigcon.backend.banks.matching.application.ManualMatchRequest;
import com.sigcon.backend.banks.matching.domain.model.Emparejamiento;
import com.sigcon.backend.banks.matching.domain.model.EmparejamientoDetalle;
import com.sigcon.backend.banks.matching.domain.model.ParametrosMatching;
import com.sigcon.backend.banks.matching.domain.model.SesionConciliacion;
import com.sigcon.backend.banks.matching.domain.repository.EmparejamientoDetalleRepository;
import com.sigcon.backend.banks.matching.domain.repository.EmparejamientoRepository;
import com.sigcon.backend.banks.matching.domain.repository.SesionConciliacionRepository;
import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.utils.UserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * BNK-HU-070: emparejamiento manual agrupado (N:1, 1:N, N:M) sobre los
 * {@link FinancialMovement} del extracto (BANK_IMPORT) y de libros (MANUAL),
 * más preview de selección, detalle y deshacer en bloque.
 */
@Service
@RequiredArgsConstructor
public class EmparejamientoService {

    private static final String C_NO = "NO_CONCILIADO";
    private static final String C_OK = "CONCILIADO";
    private static final String E_DESHECHO = "DESHECHO";
    private static final String LADO_EXT = "EXTRACTO";
    private static final String LADO_LIB = "LIBROS";

    private final FinancialMovementRepository movementRepository;
    private final EmparejamientoRepository emparejamientoRepository;
    private final EmparejamientoDetalleRepository detalleRepository;
    private final ParametrosMatchingService parametrosMatchingService;
    private final BankAccountRepository bankAccountRepository;
    private final SesionConciliacionRepository sesionConciliacionRepository;
    private final AuditPublisher auditPublisher;
    private final UserUtil userUtil;

    /** BNK-HU-070 E1: preview de la selección (sumas y diferencia en tiempo real). */
    public Map<String, Object> preview(ManualMatchRequest req) {
        List<FinancialMovement> ext = loadSide(req.getBankAccountId(), req.getExtractoIds());
        List<FinancialMovement> lib = loadSide(req.getBankAccountId(), req.getLibrosIds());
        BigDecimal sumExt = sumAbs(ext);
        BigDecimal sumLib = sumAbs(lib);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("seleccionadosLibros", lib.size());
        r.put("seleccionadosExtracto", ext.size());
        r.put("sumaLibros", sumLib);
        r.put("sumaExtracto", sumExt);
        r.put("diferencia", sumExt.subtract(sumLib).abs());
        return r;
    }

    /** BNK-HU-070 E2/E3/E4/E5: crea un emparejamiento manual con validación de sumas y motivo. */
    @Transactional
    public Map<String, Object> createManual(ManualMatchRequest req) {
        User user = userUtil.getUser();
        BankAccount ba = bankAccountRepository.findById(req.getBankAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Cuenta bancaria no encontrada."));
        ParametrosMatching p = parametrosMatchingService.getEffective(ba.getId());

        List<FinancialMovement> ext = loadSide(ba.getId(), req.getExtractoIds());
        List<FinancialMovement> lib = loadSide(ba.getId(), req.getLibrosIds());
        if (ext.isEmpty() || lib.isEmpty()) {
            throw new IllegalArgumentException("Debe seleccionar al menos un movimiento de cada lado (extracto y libros).");
        }

        // No reutilizar movimientos ya emparejados en un emparejamiento activo.
        for (FinancialMovement m : concat(ext, lib)) {
            if (C_OK.equals(m.getEstadoConciliacionSesion())) {
                throw new IllegalArgumentException("El movimiento #" + m.getId()
                        + " ya está conciliado. Deshaga su emparejamiento antes de volver a usarlo.");
            }
            for (EmparejamientoDetalle d : detalleRepository.findByFinancialMovementId(m.getId())) {
                emparejamientoRepository.findByIdAndDeletedAtIsNull(d.getEmparejamientoId()).ifPresent(emp -> {
                    throw new IllegalArgumentException("El movimiento #" + m.getId()
                            + " ya está en el emparejamiento #" + emp.getId() + ". Deshágalo primero.");
                });
            }
        }

        // HU-070 E2: mismo signo en TODOS los movimientos.
        int signo = ext.get(0).getAmount().signum();
        for (FinancialMovement m : concat(ext, lib)) {
            if (m.getAmount() == null || m.getAmount().signum() != signo) {
                throw new IllegalArgumentException("Todos los movimientos deben tener el mismo signo (todos ingresos o todos egresos).");
            }
        }

        BigDecimal sumExt = sumAbs(ext);
        BigDecimal sumLib = sumAbs(lib);
        BigDecimal diff = sumExt.subtract(sumLib).abs();

        // Tolerancia (abs o porcentual sobre el lado extracto).
        BigDecimal tol = p.getToleranciaMontoAbs();
        if (p.getToleranciaMontoPct() != null && p.getToleranciaMontoPct().signum() > 0) {
            BigDecimal tolPct = sumExt.multiply(p.getToleranciaMontoPct())
                    .divide(new BigDecimal("100"), 4, java.math.RoundingMode.HALF_UP);
            if (tolPct.compareTo(tol) > 0) tol = tolPct;
        }
        if (diff.compareTo(tol) > 0) {
            throw new IllegalArgumentException("Las sumas no coinciden: extracto $" + sumExt
                    + " vs libros $" + sumLib + " (diferencia $" + diff + ", tolerancia $" + tol
                    + "). Ajuste la selección o registre una partida conciliatoria.");
        }

        // Tipo de emparejamiento.
        String tipo;
        if (ext.size() == 1 && lib.size() == 1) tipo = "UNO_A_UNO";
        else if (ext.size() == 1 && lib.size() > 1) tipo = "N_A_UNO";   // N libros = 1 extracto
        else if (ext.size() > 1 && lib.size() == 1) tipo = "UNO_A_N";   // 1 libro = N extracto
        else tipo = "N_A_N";

        // HU-070 E4: N:M exige motivo >=30. HU-070 E5: diferencia tolerada (>0) también.
        boolean requiereMotivo = "N_A_N".equals(tipo) || diff.signum() > 0;
        if (requiereMotivo) {
            if (req.getMotivo() == null || req.getMotivo().trim().length() < 30) {
                String causa = "N_A_N".equals(tipo)
                        ? "Un emparejamiento N:M requiere un motivo"
                        : "Hay una diferencia tolerada de $" + diff + "; requiere un motivo";
                throw new IllegalArgumentException(causa + " de mínimo 30 caracteres que explique la agrupación.");
            }
        }

        Emparejamiento emp = Emparejamiento.builder()
                .companyId(ba.getCompanyId())
                .cuentaBancariaId(ba.getId())
                // QA Conciliación (2026-05-25) Bug 1: acotar a la sesión para que el Paso 5
                // liste este emparejamiento manual solo dentro de su sesión.
                .reconciliationSessionId(req.getReconciliationSessionId())
                .tipoEmparejamiento(tipo)
                .metodo("MANUAL")
                .score(100)
                .estado("CONFIRMADO")
                .sumaExtracto(sumExt)
                .sumaLibros(sumLib)
                .diferencia(diff)
                .motivoMatchManual(req.getMotivo() != null ? req.getMotivo().trim() : null)
                .confirmadoAt(LocalDateTime.now())
                .confirmadoBy(user != null ? user.getUsername() : "sistema")
                .build();
        emp = emparejamientoRepository.save(emp);

        for (FinancialMovement m : ext) saveDetalle(emp, m, LADO_EXT);
        for (FinancialMovement m : lib) saveDetalle(emp, m, LADO_LIB);

        // HU-070 E7: auditar EMPAREJAR (mapeado a UPDATE) con todos los IDs involucrados.
        String newValues = "{\"tipo\":\"" + tipo + "\",\"extractoIds\":" + req.getExtractoIds()
                + ",\"librosIds\":" + req.getLibrosIds() + ",\"sumaExtracto\":" + sumExt
                + ",\"sumaLibros\":" + sumLib + ",\"diferencia\":" + diff
                + ",\"motivo\":\"" + (emp.getMotivoMatchManual() == null ? "" : emp.getMotivoMatchManual().replace("\"", "'")) + "\"}";
        // BNK-RF-35 (QA Bloque BNK 2026-06-03): el motivo de la conciliación manual DEBE
        // quedar en la DESCRIPCIÓN del log de auditoría (no solo en newValues), porque la
        // pantalla de Auditoría muestra la columna Descripción al usuario.
        String motivoDesc = emp.getMotivoMatchManual();
        auditPublisher.publishUpdate(AuditModule.BNK, "Emparejamiento", emp.getId(),
                "EMPAREJAR manual " + tipo + " cuenta=" + ba.getId() + " emparejamiento=" + emp.getId()
                        + (motivoDesc != null && !motivoDesc.isBlank() ? " | motivo: " + motivoDesc : ""),
                null, newValues);

        return detail(emp.getId());
    }

    /** BNK-HU-070 E6: detalle del emparejamiento agregado con sus dos lados. */
    public Map<String, Object> detail(Long emparejamientoId) {
        Emparejamiento emp = emparejamientoRepository.findByIdAndDeletedAtIsNull(emparejamientoId)
                .orElseThrow(() -> new IllegalArgumentException("Emparejamiento no encontrado."));
        List<EmparejamientoDetalle> dets = detalleRepository.findByEmparejamientoId(emp.getId());
        List<Map<String, Object>> extracto = new ArrayList<>();
        List<Map<String, Object>> libros = new ArrayList<>();
        for (EmparejamientoDetalle d : dets) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("financialMovementId", d.getFinancialMovementId());
            row.put("monto", d.getMonto());
            movementRepository.findById(d.getFinancialMovementId()).ifPresent(m -> {
                row.put("fecha", m.getMovementDate());
                row.put("descripcion", m.getDescription());
            });
            if (LADO_EXT.equals(d.getLado())) extracto.add(row); else libros.add(row);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", emp.getId());
        r.put("tipo", emp.getTipoEmparejamiento());
        r.put("metodo", emp.getMetodo());
        r.put("score", emp.getScore());
        r.put("estado", emp.getEstado());
        r.put("sumaExtracto", emp.getSumaExtracto());
        r.put("sumaLibros", emp.getSumaLibros());
        r.put("diferencia", emp.getDiferencia());
        r.put("motivo", emp.getMotivoMatchManual());
        r.put("confirmadoBy", emp.getConfirmadoBy());
        r.put("extracto", extracto);
        r.put("libros", libros);
        return r;
    }

    /** BNK-HU-069 E8: confirmar una sugerencia (PROPUESTO/AMBIGUO) → CONFIRMADO + movimientos CONCILIADO. */
    @Transactional
    public Map<String, Object> confirm(Long emparejamientoId) {
        User user = userUtil.getUser();
        Emparejamiento emp = emparejamientoRepository.findByIdAndDeletedAtIsNull(emparejamientoId)
                .orElseThrow(() -> new IllegalArgumentException("Emparejamiento no encontrado."));
        if ("CONFIRMADO".equals(emp.getEstado())) return detail(emp.getId()); // idempotente
        emp.setEstado("CONFIRMADO");
        emp.setConfirmadoAt(LocalDateTime.now());
        emp.setConfirmadoBy(user != null ? user.getUsername() : "sistema");
        emparejamientoRepository.save(emp);
        for (EmparejamientoDetalle d : detalleRepository.findByEmparejamientoId(emp.getId())) {
            movementRepository.findById(d.getFinancialMovementId()).ifPresent(m -> {
                m.setEstadoConciliacionSesion(C_OK);
                movementRepository.save(m);
            });
        }
        auditPublisher.publishUpdate(AuditModule.BNK, "Emparejamiento", emp.getId(),
                "CONFIRMAR sugerencia emparejamiento=" + emp.getId() + " score=" + emp.getScore());
        return detail(emp.getId());
    }

    /**
     * BNK-RF-36 (Deshacer el cruce entre movimiento y comprobante): deshace todo el
     * emparejamiento como un bloque. Reglas del requerimiento:
     * - Motivo obligatorio (10-500 caracteres).
     * - Si la sesión de conciliación asociada está CERRADA, se rechaza (reabrir primero).
     * - Si algún movimiento fue cruzado por cheque, se deriva al módulo de cheques.
     * - No modifica el comprobante ni el asiento contable (solo se desvincula).
     * - Los movimientos vuelven a NO_CONCILIADO y queda auditado con el motivo.
     */
    @Transactional
    public Map<String, Object> undo(Long emparejamientoId, String motivo) {
        Emparejamiento emp = emparejamientoRepository.findByIdAndDeletedAtIsNull(emparejamientoId)
                .orElseThrow(() -> new IllegalArgumentException("Emparejamiento no encontrado."));
        // BNK-RF-36: motivo obligatorio 10-500.
        if (motivo == null || motivo.trim().length() < 10) {
            throw new IllegalArgumentException("Debe indicar el motivo del desemparejamiento (mínimo 10 caracteres).");
        }
        if (motivo.trim().length() > 500) {
            throw new IllegalArgumentException("El motivo del desemparejamiento no puede exceder 500 caracteres.");
        }
        List<EmparejamientoDetalle> dets = detalleRepository.findByEmparejamientoId(emp.getId());
        // BNK-RF-36: si la sesión de conciliación asociada está CERRADA, no se puede desvincular.
        if (emp.getReconciliationSessionId() != null) {
            SesionConciliacion ses = sesionConciliacionRepository
                    .findByIdAndDeletedAtIsNull(emp.getReconciliationSessionId()).orElse(null);
            if (ses != null && "CERRADA".equals(ses.getEstado())) {
                String periodo = ses.getPeriodEnd() != null
                        ? ses.getPeriodEnd().getYear() + "-" + String.format("%02d", ses.getPeriodEnd().getMonthValue())
                        : "(sin período)";
                throw new IllegalStateException("La sesión de conciliación del período " + periodo
                        + " está cerrada. Reabra la sesión para deshacer el cruce.");
            }
        }
        // BNK-RF-36: si algún movimiento del cruce está asociado a un cheque, derivar al módulo de cheques.
        for (EmparejamientoDetalle d : dets) {
            FinancialMovement m = movementRepository.findById(d.getFinancialMovementId()).orElse(null);
            if (m != null && m.getNumeroCheque() != null && !m.getNumeroCheque().isBlank()) {
                throw new IllegalStateException("No se puede desvincular un cruce realizado por cheque. "
                        + "Use el módulo de cheques para gestionar este caso.");
            }
        }
        List<Long> movIds = new ArrayList<>();
        for (EmparejamientoDetalle d : dets) {
            movementRepository.findById(d.getFinancialMovementId()).ifPresent(m -> {
                m.setEstadoConciliacionSesion(C_NO);
                movementRepository.save(m);
            });
            movIds.add(d.getFinancialMovementId());
            detalleRepository.delete(d);
        }
        emp.setEstado(E_DESHECHO);
        emp.setMotivoMatchManual(motivo.trim());
        emp.setDeletedAt(LocalDateTime.now());
        emparejamientoRepository.save(emp);

        // BNK-RF-36: auditar con el motivo en la descripción (Fecha/Usuario/Acción/Entidad/Módulo/Severidad/IP).
        auditPublisher.publishUpdate(AuditModule.BNK, "Emparejamiento", emp.getId(),
                "Deshacer cruce movimiento-comprobante emparejamiento=" + emp.getId()
                        + " movimientos=" + movIds + " | motivo: " + motivo.trim());

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", emp.getId());
        r.put("estado", E_DESHECHO);
        r.put("movimientosLiberados", movIds);
        r.put("motivo", motivo.trim());
        return r;
    }

    /**
     * Conciliación Paso 5 (7.2): rechazar un emparejamiento automático.
     * R-1: motivo obligatorio (>=10). R-3: ambos movimientos vuelven a NO_CONCILIADO
     * (quedan libres para emparejamiento manual). Devuelve los ids del extracto para que
     * el cliente encadene la generación del asiento de ajuste (R-2) en su propia
     * transacción (evita el anti-patrón rollback-only de llamar un @Transactional que lanza).
     */
    @Transactional
    public Map<String, Object> rechazar(Long emparejamientoId, String motivo) {
        if (motivo == null || motivo.trim().length() < 10) {
            throw new IllegalArgumentException("Debe indicar el motivo del rechazo (mínimo 10 caracteres).");
        }
        Emparejamiento emp = emparejamientoRepository.findByIdAndDeletedAtIsNull(emparejamientoId)
                .orElseThrow(() -> new IllegalArgumentException("Emparejamiento no encontrado."));
        List<EmparejamientoDetalle> dets = detalleRepository.findByEmparejamientoId(emp.getId());
        List<Long> extractoMovs = new ArrayList<>();
        List<Long> librosMovs = new ArrayList<>();
        for (EmparejamientoDetalle d : dets) {
            movementRepository.findById(d.getFinancialMovementId()).ifPresent(m -> {
                m.setEstadoConciliacionSesion(C_NO);
                movementRepository.save(m);
            });
            if (LADO_EXT.equals(d.getLado())) extractoMovs.add(d.getFinancialMovementId());
            else librosMovs.add(d.getFinancialMovementId());
            detalleRepository.delete(d);
        }
        emp.setEstado(E_DESHECHO);
        emp.setMotivoMatchManual(motivo.trim());
        emp.setDeletedAt(LocalDateTime.now());
        emparejamientoRepository.save(emp);
        auditPublisher.publishUpdate(AuditModule.BNK, "Emparejamiento", emp.getId(),
                "RECHAZAR_EMPAREJAMIENTO emparejamiento=" + emp.getId() + " | motivo=" + motivo.trim()
                        + " | extracto=" + extractoMovs + " libros=" + librosMovs);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", emp.getId());
        r.put("estado", E_DESHECHO);
        r.put("motivo", motivo.trim());
        r.put("extractoMovimientos", extractoMovs);
        r.put("librosMovimientos", librosMovs);
        return r;
    }

    /** Lista los emparejamientos de una cuenta (workspace). */
    public List<Map<String, Object>> listForAccount(Long bankAccountId) {
        return emparejamientoRepository.findByCuentaBancariaIdAndDeletedAtIsNullOrderByIdDesc(bankAccountId)
                .stream().map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", e.getId());
                    m.put("tipo", e.getTipoEmparejamiento());
                    m.put("metodo", e.getMetodo());
                    m.put("score", e.getScore());
                    m.put("estado", e.getEstado());
                    m.put("sumaExtracto", e.getSumaExtracto());
                    m.put("sumaLibros", e.getSumaLibros());
                    m.put("diferencia", e.getDiferencia());
                    return m;
                }).toList();
    }

    /**
     * BNK-HU-069/070: datos para el workspace de conciliación de una cuenta:
     * movimientos libres (NO_CONCILIADO) de cada lado para emparejar manualmente,
     * y los emparejamientos activos con su detalle para revisar/deshacer.
     */
    public Map<String, Object> getWorkspace(Long bankAccountId) {
        bankAccountRepository.findById(bankAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Cuenta bancaria no encontrada."));
        List<Map<String, Object>> extracto = movementRepository
                .findByBankAccount_IdAndSourceType(bankAccountId,
                        com.sigcon.backend.banks.financialmovements.domain.model.enums.FinancialMovementSourceType.BANK_IMPORT)
                .stream().filter(m -> C_NO.equals(m.getEstadoConciliacionSesion()) || m.getEstadoConciliacionSesion() == null)
                .map(this::movRow).toList();
        List<Map<String, Object>> libros = movementRepository
                .findByBankAccount_IdAndSourceType(bankAccountId,
                        com.sigcon.backend.banks.financialmovements.domain.model.enums.FinancialMovementSourceType.MANUAL)
                .stream().filter(m -> C_NO.equals(m.getEstadoConciliacionSesion()) || m.getEstadoConciliacionSesion() == null)
                .map(this::movRow).toList();
        List<Map<String, Object>> emparejamientos = emparejamientoRepository
                .findByCuentaBancariaIdAndDeletedAtIsNullOrderByIdDesc(bankAccountId)
                .stream().map(e -> detail(e.getId())).toList();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("cuentaBancariaId", bankAccountId);
        r.put("extracto", extracto);
        r.put("libros", libros);
        r.put("emparejamientos", emparejamientos);
        return r;
    }

    /**
     * QA Conciliación (2026-05-25) Bug 1: emparejamientos de UNA sesión (Paso 5).
     * Reemplaza a {@link #listForAccount} en el flujo guiado para que al cambiar de
     * sesión no se arrastren los emparejamientos de la sesión anterior.
     */
    public List<Map<String, Object>> listForSession(Long sesionId) {
        return emparejamientoRepository.findByReconciliationSessionIdAndDeletedAtIsNullOrderByIdDesc(sesionId)
                .stream().map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", e.getId());
                    m.put("tipo", e.getTipoEmparejamiento());
                    m.put("metodo", e.getMetodo());
                    m.put("score", e.getScore());
                    m.put("estado", e.getEstado());
                    m.put("sumaExtracto", e.getSumaExtracto());
                    m.put("sumaLibros", e.getSumaLibros());
                    m.put("diferencia", e.getDiferencia());
                    return m;
                }).toList();
    }

    /**
     * QA Conciliación (2026-05-25) Bug 4: workspace ACOTADO a una sesión (Paso 6).
     * Extracto = movimientos importados bajo la sesión (sesion_conciliacion_id);
     * libros = MANUAL dentro del período de la sesión. Ambos NO_CONCILIADO. Replica
     * exactamente el pooling del motor (runEngineForSession), de modo que "Libros
     * libres" no muestre movimientos de otras sesiones ni del ámbito global.
     */
    public Map<String, Object> getWorkspaceForSession(Long sesionId) {
        SesionConciliacion s = sesionConciliacionRepository.findByIdAndDeletedAtIsNull(sesionId)
                .orElseThrow(() -> new IllegalArgumentException("Sesión de conciliación no encontrada."));
        List<Map<String, Object>> extracto = movementRepository
                .findBySesionConciliacionIdOrderByMovementDateAscIdAsc(sesionId)
                .stream()
                .filter(m -> com.sigcon.backend.banks.financialmovements.domain.model.enums.FinancialMovementSourceType.BANK_IMPORT.equals(m.getSourceType()))
                .filter(m -> C_NO.equals(m.getEstadoConciliacionSesion()) || m.getEstadoConciliacionSesion() == null)
                .map(this::movRow).toList();
        List<Map<String, Object>> libros = movementRepository
                .findByBankAccountSourceTypeAndPeriod(s.getBankAccountId(),
                        com.sigcon.backend.banks.financialmovements.domain.model.enums.FinancialMovementSourceType.MANUAL,
                        s.getPeriodStart(), s.getPeriodEnd())
                .stream().filter(m -> C_NO.equals(m.getEstadoConciliacionSesion()) || m.getEstadoConciliacionSesion() == null)
                .map(this::movRow).toList();
        List<Map<String, Object>> emparejamientos = emparejamientoRepository
                .findByReconciliationSessionIdAndDeletedAtIsNullOrderByIdDesc(sesionId)
                .stream().map(e -> detail(e.getId())).toList();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("sesionId", sesionId);
        r.put("cuentaBancariaId", s.getBankAccountId());
        r.put("extracto", extracto);
        r.put("libros", libros);
        r.put("emparejamientos", emparejamientos);
        return r;
    }

    private Map<String, Object> movRow(FinancialMovement m) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", m.getId());
        r.put("fecha", m.getMovementDate());
        r.put("descripcion", m.getDescription());
        r.put("monto", m.getAmount());
        r.put("referencia", m.getExternalReference());
        r.put("cheque", m.getNumeroCheque());
        r.put("nit", m.getNitDetectado());
        r.put("tipoMovimiento", m.getTipoMovimiento());
        r.put("estadoConciliacion", m.getEstadoConciliacionSesion());
        return r;
    }

    // ---- helpers ----

    private void saveDetalle(Emparejamiento emp, FinancialMovement m, String lado) {
        detalleRepository.save(EmparejamientoDetalle.builder()
                .companyId(emp.getCompanyId())
                .emparejamientoId(emp.getId())
                .financialMovementId(m.getId())
                .lado(lado)
                .monto(m.getAmount())
                .build());
        m.setEstadoConciliacionSesion(C_OK);
        movementRepository.save(m);
    }

    private List<FinancialMovement> loadSide(Long bankAccountId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) return new ArrayList<>();
        List<FinancialMovement> out = new ArrayList<>();
        for (Long id : ids) {
            FinancialMovement m = movementRepository.findByIdAndBankAccount_Id(id, bankAccountId)
                    .orElseThrow(() -> new IllegalArgumentException("Movimiento #" + id + " no encontrado en la cuenta."));
            out.add(m);
        }
        return out;
    }

    private BigDecimal sumAbs(List<FinancialMovement> list) {
        return list.stream().map(m -> m.getAmount() == null ? BigDecimal.ZERO : m.getAmount().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<FinancialMovement> concat(List<FinancialMovement> a, List<FinancialMovement> b) {
        List<FinancialMovement> r = new ArrayList<>(a);
        r.addAll(b);
        return r;
    }
}
