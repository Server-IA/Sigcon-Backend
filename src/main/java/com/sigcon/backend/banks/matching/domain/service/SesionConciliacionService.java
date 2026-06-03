package com.sigcon.backend.banks.matching.domain.service;

import com.sigcon.backend.audit.domain.model.enums.AuditAction;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.model.enums.AuditSeverity;
import com.sigcon.backend.audit.domain.service.AuditLogService;
import com.sigcon.backend.audit.domain.service.AuditPublisher;
import com.sigcon.backend.banks.bankaccounts.domain.model.BankAccount;
import com.sigcon.backend.banks.bankaccounts.domain.repository.BankAccountRepository;
import com.sigcon.backend.banks.financialmovements.domain.model.FinancialMovement;
import com.sigcon.backend.banks.financialmovements.domain.model.enums.FinancialMovementSourceType;
import com.sigcon.backend.banks.financialmovements.domain.repository.FinancialMovementRepository;
import com.sigcon.backend.banks.matching.domain.model.*;
import com.sigcon.backend.banks.matching.domain.repository.*;
import com.sigcon.backend.general.accounting.AccountingPeriod;
import com.sigcon.backend.general.accounting.AccountingPeriodRepository;
import com.sigcon.backend.general.accounting.journal.domain.model.JournalEntry;
import com.sigcon.backend.general.accounting.journal.domain.repository.JournalEntryRepository;
import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.platform.tenant.TenantContext;
import com.sigcon.backend.utils.UserUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * BNK-HU-066/067/075/077: máquina de estados de la conciliación firmada.
 * BORRADOR -> (firma elaborador) -> EN_REVISION -> (firma revisor + segregación)
 * -> APROBADA -> (cierre + PDF) -> CERRADA -> (reapertura) -> REABIERTA(v+1).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SesionConciliacionService {

    private static final Set<String> REVIEWER_ROLES = Set.of("REVISOR_FISCAL", "SUPERVISOR_CONCILIACION", "ADMIN_EMPRESA");
    private static final Set<String> ELAB_ROLES = Set.of("CONCILIADOR", "CONTADOR", "ADMIN_EMPRESA", "SUPERVISOR_CONCILIACION");

    private final SesionConciliacionRepository sesionRepo;
    private final SolicitudReaperturaRepository solicitudRepo;
    private final FirmaElectronicaRepository firmaRepo;
    private final PartidaConciliatoriaRepository partidaRepo;
    private final EmparejamientoRepository emparejamientoRepo;
    private final EmparejamientoDetalleRepository emparejamientoDetalleRepo;
    private final FinancialMovementRepository movementRepo;
    private final BankAccountRepository bankAccountRepo;
    private final AccountingPeriodRepository periodRepo;
    private final JournalEntryRepository journalRepo;
    private final ElectronicSignatureService signatureService;
    private final ReconciliationReportPdfService pdfService;
    private final AuditPublisher auditPublisher;
    private final AuditLogService auditLogService;
    private final UserUtil userUtil;

    // ===================== ciclo de vida =====================

    @Transactional
    public Map<String, Object> create(Long bankAccountId, LocalDate ps, LocalDate pe, BigDecimal saldoExtracto) {
        // Conciliación Sec 3.2: validaciones de fechas del período (V1, V2).
        if (ps == null || pe == null) {
            throw new IllegalArgumentException("Debe indicar la fecha inicial y la fecha final del período.");
        }
        if (pe.isBefore(ps)) {
            throw new IllegalArgumentException("La fecha final no puede ser anterior a la fecha inicial.");
        }
        if (pe.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha final de conciliación no puede ser una fecha futura.");
        }
        // CONC-4 (QA reeval Q3): NO permitir que el período de la nueva sesión se SOLAPE
        // con el de otra sesión existente de la misma cuenta. Antes solo se bloqueaba
        // cuando las fechas eran EXACTAMENTE iguales (4→20 vs 4→21 pasaba), lo que permitía
        // conciliar dos veces los mismos días. Dos rangos [ps,pe] y [xs,xe] se solapan si
        // ps <= xe && xs <= pe. La re-conciliación de un período ya cerrado se hace por
        // reapertura/versionado, no creando otra sesión solapada.
        SesionConciliacion solapada = sesionRepo.findByBankAccountIdAndDeletedAtIsNullOrderByIdDesc(bankAccountId)
                .stream()
                .filter(x -> x.getPeriodStart() != null && x.getPeriodEnd() != null
                        && !ps.isAfter(x.getPeriodEnd()) && !x.getPeriodStart().isAfter(pe))
                .findFirst().orElse(null);
        if (solapada != null) {
            // Si el conflicto es con un BORRADOR, el usuario puede eliminarlo para
            // reajustar las fechas sin interrumpir el flujo (se permite borrar borradores).
            String ayuda = "BORRADOR".equals(solapada.getEstado())
                    ? " Puede eliminar esa sesión en borrador (#" + solapada.getId()
                      + ") y volver a crear la conciliación con las fechas ajustadas."
                    : " Para re-conciliar ese período use la opción de reapertura sobre la sesión existente.";
            throw new IllegalArgumentException(
                "El período " + ps + " → " + pe + " se solapa con la sesión #" + solapada.getId()
                + " (" + solapada.getPeriodStart() + " → " + solapada.getPeriodEnd()
                + ", estado " + solapada.getEstado() + "). Una conciliación no debe contener fechas"
                + " que ya están cubiertas por otra sesión." + ayuda);
        }
        BankAccount ba = bankAccountRepo.findById(bankAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Cuenta bancaria no encontrada"));
        User u = userUtil.getUser();
        BigDecimal saldoLibros = movementRepo.findAllByBankAccountIdOrdered(bankAccountId).stream()
                .filter(m -> "MANUAL".equals(m.getSourceType() != null ? m.getSourceType().name() : ""))
                .map(m -> m.getAmount() == null ? BigDecimal.ZERO : m.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal extracto = saldoExtracto != null ? saldoExtracto : BigDecimal.ZERO;
        SesionConciliacion s = SesionConciliacion.builder()
                .companyId(ba.getCompanyId()).bankAccountId(bankAccountId)
                .periodStart(ps).periodEnd(pe)
                .estado("BORRADOR").version(1)
                .saldoExtracto(extracto).saldoLibros(saldoLibros)
                .diferencia(extracto.subtract(saldoLibros))
                .createdBy(u != null ? u.getId() : null)
                .build();
        s = sesionRepo.save(s);
        auditPublisher.publishCreate(AuditModule.BNK, "SesionConciliacion", s.getId(),
                "Sesión de conciliación creada (BORRADOR) cuenta=" + bankAccountId);
        return detail(s.getId());
    }

    /**
     * QA reeval Q3: eliminar (soft-delete) una sesión en BORRADOR. Permite al usuario
     * reajustar fechas cuando un borrador solapa el rango que quiere conciliar, sin
     * interrumpir el flujo de sobre-poner fechas. Solo BORRADOR: las sesiones en revisión,
     * aprobadas, cerradas o reabiertas conservan su trazabilidad (usar reapertura/versionado).
     */
    @Transactional
    public Map<String, Object> deleteBorrador(Long sesionId) {
        SesionConciliacion s = load(sesionId);
        if (!"BORRADOR".equals(s.getEstado())) {
            throw new IllegalStateException(
                "Solo se pueden eliminar sesiones en estado BORRADOR (actual: " + s.getEstado()
                + "). Las conciliaciones en revisión, aprobadas o cerradas conservan su trazabilidad.");
        }
        // QA Bloque BNK (2026-06-03) Bug 1 (BNK-RF-23): un BORRADOR que se abandona NO debe
        // dejar sus movimientos marcados como CONCILIADO. Al eliminar el borrador, se revierten
        // los emparejamientos de la sesión (soft-delete) y sus movimientos vuelven a
        // NO_CONCILIADO, de modo que queden disponibles para una conciliación posterior.
        int movRevertidos = 0;
        for (Emparejamiento emp : emparejamientoRepo
                .findByReconciliationSessionIdAndDeletedAtIsNullOrderByIdDesc(sesionId)) {
            for (EmparejamientoDetalle d : emparejamientoDetalleRepo.findByEmparejamientoId(emp.getId())) {
                FinancialMovement m = movementRepo.findById(d.getFinancialMovementId()).orElse(null);
                if (m != null && "CONCILIADO".equals(m.getEstadoConciliacionSesion())) {
                    m.setEstadoConciliacionSesion("NO_CONCILIADO");
                    movementRepo.save(m);
                    movRevertidos++;
                }
                emparejamientoDetalleRepo.delete(d);
            }
            emp.setEstado("DESHECHO");
            emp.setDeletedAt(LocalDateTime.now());
            emparejamientoRepo.save(emp);
        }
        s.setDeletedAt(LocalDateTime.now());
        sesionRepo.save(s);
        auditPublisher.publish(AuditAction.DELETE, AuditModule.BNK, AuditSeverity.MEDIUM,
                "SesionConciliacion", sesionId,
                "Sesión de conciliación en BORRADOR eliminada (período "
                + s.getPeriodStart() + " → " + s.getPeriodEnd() + "). Movimientos revertidos a NO_CONCILIADO: "
                + movRevertidos, null, null, null);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("deleted", true);
        r.put("id", sesionId);
        r.put("movimientosRevertidos", movRevertidos);
        return r;
    }

    public List<Map<String, Object>> list(Long bankAccountId) {
        return sesionRepo.findByBankAccountIdAndDeletedAtIsNullOrderByIdDesc(bankAccountId).stream()
                .map(this::row).toList();
    }

    /** Conciliación Sec 4 (Paso 2): libros contables (MANUAL) del período de la sesión. */
    public List<Map<String, Object>> librosDelPeriodo(Long sesionId) {
        SesionConciliacion s = load(sesionId);
        return movementRepo.findByBankAccountSourceTypeAndPeriod(
                        s.getBankAccountId(),
                        com.sigcon.backend.banks.financialmovements.domain.model.enums.FinancialMovementSourceType.MANUAL,
                        s.getPeriodStart(), s.getPeriodEnd())
                .stream().map(this::movRow).toList();
    }

    /** Conciliación Sec 5 (Paso 3): extracto bancario importado bajo la sesión. */
    public List<Map<String, Object>> extractoDelPeriodo(Long sesionId) {
        load(sesionId); // valida existencia + tenant
        return movementRepo.findBySesionConciliacionIdOrderByMovementDateAscIdAsc(sesionId)
                .stream().map(this::movRow).toList();
    }

    /**
     * Conciliación Sec 7 (C1 / Paso 7): resumen para el cierre en cero.
     * Cuenta extracto (bajo la sesión) y libros (MANUAL del período) y cuántos
     * de cada lado quedan sin conciliar. enCero = no hay movimientos pendientes.
     */
    public Map<String, Object> resumenCierre(Long sesionId) {
        SesionConciliacion s = load(sesionId);
        List<FinancialMovement> ext = movementRepo.findBySesionConciliacionIdOrderByMovementDateAscIdAsc(s.getId());
        List<FinancialMovement> lib = movementRepo.findByBankAccountSourceTypeAndPeriod(
                s.getBankAccountId(),
                com.sigcon.backend.banks.financialmovements.domain.model.enums.FinancialMovementSourceType.MANUAL,
                s.getPeriodStart(), s.getPeriodEnd());
        long extConc = ext.stream().filter(m -> "CONCILIADO".equals(m.getEstadoConciliacionSesion())).count();
        long libConc = lib.stream().filter(m -> "CONCILIADO".equals(m.getEstadoConciliacionSesion())).count();
        long pendientes = (ext.size() - extConc) + (lib.size() - libConc);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("sesionId", s.getId());
        r.put("estado", s.getEstado());
        r.put("version", s.getVersion());
        r.put("totalExtracto", ext.size());
        r.put("extractoConciliado", extConc);
        r.put("totalLibros", lib.size());
        r.put("librosConciliado", libConc);
        r.put("pendientes", pendientes);
        r.put("enCero", pendientes == 0);
        // Bug 6: saldos recalculados por sesión (no el valor almacenado).
        BigDecimal[] saldos = computeSaldos(s);
        r.put("saldoExtracto", saldos[0]);
        r.put("saldoLibros", saldos[1]);
        r.put("diferencia", saldos[2]);
        r.put("firmaElaboradorId", s.getFirmaElaboradorId());
        r.put("firmaRevisorId", s.getFirmaRevisorId());
        r.put("informeArchivoId", s.getInformeArchivoId());
        return r;
    }

    /** C1: movimientos de la sesión (extracto + libros del período) que aún no están CONCILIADO. */
    private List<FinancialMovement> pendientesDeLaSesion(SesionConciliacion s) {
        List<FinancialMovement> pend = new ArrayList<>();
        for (FinancialMovement m : movementRepo.findBySesionConciliacionIdOrderByMovementDateAscIdAsc(s.getId()))
            if (!"CONCILIADO".equals(m.getEstadoConciliacionSesion())) pend.add(m);
        for (FinancialMovement m : movementRepo.findByBankAccountSourceTypeAndPeriod(
                s.getBankAccountId(),
                com.sigcon.backend.banks.financialmovements.domain.model.enums.FinancialMovementSourceType.MANUAL,
                s.getPeriodStart(), s.getPeriodEnd()))
            if (!"CONCILIADO".equals(m.getEstadoConciliacionSesion())) pend.add(m);
        return pend;
    }

    /** C1: exige conciliación en cero antes de avanzar hacia el cierre. */
    private void assertConciliacionEnCero(SesionConciliacion s) {
        List<FinancialMovement> pend = pendientesDeLaSesion(s);
        if (!pend.isEmpty()) {
            throw new IllegalStateException("BNK-CON-030: la conciliación no está en cero. Quedan "
                    + pend.size() + " movimiento(s) sin conciliar en el período. Concílielos o genere los"
                    + " asientos de ajuste (Paso 6) antes de continuar con el cierre.");
        }
    }

    /**
     * QA Bloque BNK (2026-06-03) Bug 1: COMMIT del estado oficial al CERRAR la conciliación.
     * Es el ÚNICO punto donde los movimientos cambian su estado OFICIAL (`estado_conciliacion`,
     * la "trazabilidad actual" que leen DIAN/TRM/otros módulos). Toma el estado de TRABAJO de la
     * sesión (`estado_conciliacion_sesion`) y, para los movimientos del período conciliados en la
     * sesión, lo refleja en el oficial. Como el cierre exige conciliación en cero, todos quedan
     * oficialmente CONCILIADO tras la firma + cierre.
     */
    private int commitConciliacionOficial(SesionConciliacion s) {
        int n = 0;
        List<FinancialMovement> movs = new ArrayList<>();
        movs.addAll(movementRepo.findBySesionConciliacionIdOrderByMovementDateAscIdAsc(s.getId()));
        movs.addAll(movementRepo.findByBankAccountSourceTypeAndPeriod(
                s.getBankAccountId(),
                com.sigcon.backend.banks.financialmovements.domain.model.enums.FinancialMovementSourceType.MANUAL,
                s.getPeriodStart(), s.getPeriodEnd()));
        for (FinancialMovement m : movs) {
            if ("CONCILIADO".equals(m.getEstadoConciliacionSesion())
                    && !"CONCILIADO".equals(m.getEstadoConciliacion())) {
                m.setEstadoConciliacion("CONCILIADO");
                movementRepo.save(m);
                n++;
            }
        }
        return n;
    }

    private Map<String, Object> movRow(FinancialMovement m) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", m.getId());
        r.put("fecha", m.getMovementDate() != null ? m.getMovementDate().toString() : null);
        r.put("importe", m.getAmount());
        r.put("descripcion", m.getDescription());
        r.put("referencia", m.getExternalReference());
        r.put("origen", m.getSourceType() != null ? m.getSourceType().name() : null);
        r.put("estadoConciliacion", m.getEstadoConciliacion());
        r.put("estadoConciliacionSesion", m.getEstadoConciliacionSesion());
        // HU-068: clasificación del pre-procesamiento (para ver/corregir desde el Paso 3).
        r.put("descripcionNormalizada", m.getDescripcionNormalizada());
        r.put("numeroCheque", m.getNumeroCheque());
        r.put("nitDetectado", m.getNitDetectado());
        r.put("tipoMovimiento", m.getTipoMovimiento());
        r.put("clasificacionConfianza", m.getClasificacionConfianza());
        r.put("cuentaPucSugerida", m.getCuentaPucSugerida());
        r.put("matchedVoucherId", m.getMatchedVoucherId());
        r.put("matchedJournalEntryId", m.getMatchedJournalEntryId());
        r.put("matchedCheckId", m.getMatchedCheckId());
        // QA Bloque BNK (2026-06-03) Bug 1: el bloqueo "Ya conciliado" 🔒 (solo-lectura) SOLO
        // aplica cuando el estado OFICIAL del registro está CONCILIADO, es decir, cuando una
        // sesión ANTERIOR ya cerró y commiteó. Mientras la sesión actual está abierta, los
        // movimientos emparejados muestran su estado de TRABAJO (Conciliado/No conciliado en
        // sesión) pero NO quedan bloqueados ni tocan la trazabilidad oficial.
        boolean bloqueado = "CONCILIADO".equals(m.getEstadoConciliacion());
        r.put("bloqueado", bloqueado);
        r.put("estadoVista", bloqueado ? "YA_CONCILIADO_BLOQUEADO" : m.getEstadoConciliacionSesion());
        return r;
    }

    public Map<String, Object> detail(Long sesionId) {
        SesionConciliacion s = load(sesionId);
        Map<String, Object> r = row(s);
        r.put("firmas", firmaRepo.findBySesionIdOrderByIdAsc(sesionId).stream().map(f -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("rol", f.getRolFirma()); m.put("firmante", f.getFirmanteNombre());
            m.put("tp", f.getFirmanteTp()); m.put("metodo", f.getMetodoFirma());
            m.put("fecha", f.getSelloTiempo() != null ? f.getSelloTiempo().toString() : null);
            return m;
        }).toList());
        return r;
    }

    /** BNK-HU-066 E2/E3: firma del elaborador o del revisor (2 pasos OTP). */
    @Transactional
    public Map<String, Object> firmar(Long sesionId, String rolFirma, String documento, String tp, String metodo, String otp) {
        SesionConciliacion s = load(sesionId);
        User u = userUtil.getUser();
        String payload = buildSignaturePayload(s, u, rolFirma, documento, tp);
        Map<String, Object> res = signatureService.sign(s, rolFirma, payload, u, documento, tp, metodo, otp);
        if (Boolean.FALSE.equals(res.get("otpRequired")) && res.get("firmaId") != null) {
            Long firmaId = ((Number) res.get("firmaId")).longValue();
            if ("RESPONSABLE".equals(rolFirma)) {
                // QA Conciliación (2026-05-25) Bug 2/5: firma ÚNICA de responsable. El
                // negocio no exige segregación; una sola persona firma y cierra. Se
                // registra como ambas firmas (elaborador y revisor apuntan a la misma)
                // para satisfacer la verificación de firmas y el PDF firmado sin pedir
                // un segundo firmante distinto.
                s.setFirmaElaboradorId(firmaId);
                s.setFirmaRevisorId(firmaId);
            } else if ("REVISOR".equals(rolFirma)) {
                s.setFirmaRevisorId(firmaId);
            } else {
                s.setFirmaElaboradorId(firmaId);
            }
            sesionRepo.save(s);
        }
        return res;
    }

    /** BNK-HU-066 E2: enviar a revisión exige firma del elaborador. */
    @Transactional
    public Map<String, Object> sendToReview(Long sesionId) {
        SesionConciliacion s = load(sesionId);
        if (!"BORRADOR".equals(s.getEstado()) && !"REABIERTA".equals(s.getEstado()))
            throw new IllegalStateException("Solo se envía a revisión una sesión en BORRADOR (actual: " + s.getEstado() + ").");
        // C1: no se inicia el flujo de cierre hasta que la conciliación esté en cero.
        assertConciliacionEnCero(s);
        if (s.getFirmaElaboradorId() == null)
            throw new IllegalStateException("Sin la firma del elaborador no se permite transicionar a EN_REVISION. Firme primero como elaborador.");
        User u = userUtil.getUser();
        s.setEstado("EN_REVISION");
        s.setEnviadaRevisionBy(u != null ? u.getId() : null);
        s.setEnviadaRevisionAt(LocalDateTime.now());
        sesionRepo.save(s);
        auditPublisher.publishUpdate(AuditModule.BNK, "SesionConciliacion", sesionId, "Enviada a revisión (EN_REVISION)");
        return detail(sesionId);
    }

    /** BNK-HU-067 E1 + HU-066 E3: aprobar con segregación + firma del revisor. */
    @Transactional
    public Map<String, Object> approve(Long sesionId) {
        SesionConciliacion s = load(sesionId);
        if (!"EN_REVISION".equals(s.getEstado()))
            throw new IllegalStateException("Solo se aprueba una sesión EN_REVISION (actual: " + s.getEstado() + ").");
        User u = userUtil.getUser();
        checkSegregation(s, u, "aprobar");
        if (s.getFirmaRevisorId() == null)
            throw new IllegalStateException("La aprobación solo se confirma con la firma del revisor. Firme primero como revisor.");
        s.setEstado("APROBADA");
        s.setAprobadaBy(u.getId());
        s.setAprobadaAt(LocalDateTime.now());
        sesionRepo.save(s);
        auditPublisher.publishUpdate(AuditModule.BNK, "SesionConciliacion", sesionId, "Aprobada por user=" + u.getId() + " (APROBADA)");
        return detail(sesionId);
    }

    /** BNK-HU-067 E3 + HU-077: cerrar (revisor = quien firmó/aprobó) + generar PDF firmado. */
    @Transactional
    public Map<String, Object> close(Long sesionId) {
        SesionConciliacion s = load(sesionId);
        if (!"APROBADA".equals(s.getEstado()))
            throw new IllegalStateException("Solo se cierra una sesión APROBADA (actual: " + s.getEstado() + ").");
        // C1: el cierre solo procede con la conciliación en cero (sin pendientes).
        assertConciliacionEnCero(s);
        User u = userUtil.getUser();
        if (!hasAnyRole(u, REVIEWER_ROLES))
            throw new IllegalStateException("BNK-CON-013: solo un REVISOR_FISCAL puede cerrar la conciliación.");
        if (s.getFirmaElaboradorId() == null || s.getFirmaRevisorId() == null)
            throw new IllegalStateException("BNK-CON-013: deben estar presentes ambas firmas (elaborador y revisor) para cerrar.");
        if (s.getAprobadaBy() != null && !s.getAprobadaBy().equals(u.getId()) && !s.getModoFlexible())
            throw new IllegalStateException("BNK-CON-013: el revisor que cierra debe ser el mismo que aprobó/firmó.");
        var archivo = pdfService.generateAndStore(s, u.getId());
        s.setInformeArchivoId(archivo.getId());
        s.setEstado("CERRADA");
        s.setCerradaBy(u.getId());
        s.setCerradaAt(LocalDateTime.now());
        sesionRepo.save(s);
        // Bug 1: SOLO al cerrar se commitea el estado oficial de los registros.
        int committed = commitConciliacionOficial(s);
        auditPublisher.publishUpdate(AuditModule.BNK, "SesionConciliacion", sesionId,
                "Cerrada por user=" + u.getId() + " | informe=" + archivo.getId() + " | hash=" + archivo.getHashSha256()
                        + " | registros conciliados oficialmente=" + committed);
        Map<String, Object> r = detail(sesionId);
        r.put("informeArchivoId", archivo.getId());
        r.put("informeHash", archivo.getHashSha256());
        return r;
    }

    /**
     * QA Conciliación (2026-05-25) Bug 2/5: cierre con firma ÚNICA de "Responsable".
     * El negocio NO exige segregación de funciones — la misma persona firma y cierra.
     * Transiciona BORRADOR/REABIERTA -> CERRADA en un solo paso (sin EN_REVISION ni
     * APROBADA), exige conciliación en cero + la firma del responsable, y genera el
     * informe PDF firmado. No aplica {@code checkSegregation} ni "aprobador distinto".
     */
    @Transactional
    public Map<String, Object> cerrarComoResponsable(Long sesionId) {
        SesionConciliacion s = load(sesionId);
        // Se cierra desde cualquier estado abierto (BORRADOR/REABIERTA y también
        // EN_REVISION/APROBADA de sesiones previas al cambio de flujo). Solo se rechaza
        // si ya está CERRADA.
        if ("CERRADA".equals(s.getEstado()))
            throw new IllegalStateException("La conciliación ya está cerrada.");
        // C1: el cierre solo procede con la conciliación en cero (sin pendientes).
        assertConciliacionEnCero(s);
        if (s.getFirmaElaboradorId() == null)
            throw new IllegalStateException("Debe firmar como responsable antes de cerrar la conciliación.");
        User u = userUtil.getUser();
        var archivo = pdfService.generateAndStore(s, u != null ? u.getId() : null);
        s.setInformeArchivoId(archivo.getId());
        s.setEstado("CERRADA");
        s.setAprobadaBy(u != null ? u.getId() : null);
        s.setAprobadaAt(LocalDateTime.now());
        s.setCerradaBy(u != null ? u.getId() : null);
        s.setCerradaAt(LocalDateTime.now());
        sesionRepo.save(s);
        // Bug 1: SOLO al cerrar se commitea el estado oficial de los registros.
        int committed = commitConciliacionOficial(s);
        auditPublisher.publishUpdate(AuditModule.BNK, "SesionConciliacion", sesionId,
                "Cerrada por responsable user=" + (u != null ? u.getId() : null)
                        + " | informe=" + archivo.getId() + " | hash=" + archivo.getHashSha256()
                        + " | registros conciliados oficialmente=" + committed);
        Map<String, Object> r = detail(sesionId);
        r.put("informeArchivoId", archivo.getId());
        r.put("informeHash", archivo.getHashSha256());
        return r;
    }

    // ===================== reapertura (HU-075) =====================

    @Transactional
    public Map<String, Object> solicitarReapertura(Long sesionId, String motivo, String tipoCambio,
                                                    String evidenciaFileName, String evidenciaHash) {
        SesionConciliacion s = load(sesionId);
        if (!"CERRADA".equals(s.getEstado()))
            throw new IllegalStateException("Solo se puede solicitar reapertura de una sesión CERRADA (actual: " + s.getEstado() + ").");
        if (motivo == null || motivo.trim().length() < 100)
            throw new IllegalArgumentException("El motivo de la reapertura debe tener al menos 100 caracteres.");
        if (evidenciaFileName == null || evidenciaFileName.isBlank())
            throw new IllegalArgumentException("Debe adjuntar un archivo de evidencia (correo, oficio, captura, etc.).");
        // HU-075 E2: bloquear si el período contable está LOCKED
        if (s.getPeriodEnd() != null) {
            Optional<AccountingPeriod> p = periodRepo.findByYearAndMonth(s.getPeriodEnd().getYear(), s.getPeriodEnd().getMonthValue());
            if (p.isPresent() && "LOCKED".equals(p.get().getStatus().name())) {
                throw new IllegalStateException("El período " + s.getPeriodEnd().getYear() + "-"
                        + String.format("%02d", s.getPeriodEnd().getMonthValue())
                        + " está bloqueado definitivamente. Los ajustes deben hacerse como hechos del período actual con notas explicativas (NIC 8 — corrección de errores).");
            }
        }
        User u = userUtil.getUser();
        SolicitudReapertura sol = SolicitudReapertura.builder()
                .companyId(s.getCompanyId()).sesionId(sesionId)
                .solicitanteId(u != null ? u.getId() : null)
                .motivo(motivo.trim()).tipoCambioEsperado(tipoCambio)
                .evidenciaFileName(evidenciaFileName).evidenciaHash(evidenciaHash)
                .estado("PENDIENTE").build();
        sol = solicitudRepo.save(sol);
        auditPublisher.publishCreate(AuditModule.BNK, "SolicitudReapertura", sol.getId(),
                "Solicitud de reapertura sesión=" + sesionId + " por user=" + (u != null ? u.getId() : null));
        log.info("HU-075 E1: notificar a REVISOR_FISCAL la solicitud {} (sin SMTP: solo log).", sol.getId());
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("solicitudId", sol.getId()); r.put("estado", "PENDIENTE");
        return r;
    }

    /**
     * QA reeval Q3: REAPERTURA DIRECTA en UN SOLO PASO, sin segregación de funciones.
     * Decisión del negocio: este software NO exige dos personas (solicitante + aprobador)
     * para reabrir una conciliación. El mismo usuario la reabre indicando el motivo. Se crea
     * una nueva versión REABIERTA preservando la anterior intacta (versionado). Mantiene el
     * bloqueo de período contable LOCKED por ser una regla contable (NIC 8), no de segregación.
     * La evidencia es opcional. Reemplaza el flujo solicitar/aprobar para el uso normal.
     */
    @Transactional
    public Map<String, Object> reabrir(Long sesionId, String motivo,
                                       String evidenciaFileName, String evidenciaHash) {
        SesionConciliacion orig = load(sesionId);
        if (!"CERRADA".equals(orig.getEstado()))
            throw new IllegalStateException("Solo se puede reabrir una sesión CERRADA (actual: " + orig.getEstado() + ").");
        if (motivo == null || motivo.trim().length() < 20)
            throw new IllegalArgumentException("Indique el motivo de la reapertura (mínimo 20 caracteres).");
        // Regla contable (NIC 8): no reabrir si el período contable está bloqueado definitivamente.
        if (orig.getPeriodEnd() != null) {
            Optional<AccountingPeriod> p = periodRepo.findByYearAndMonth(
                    orig.getPeriodEnd().getYear(), orig.getPeriodEnd().getMonthValue());
            if (p.isPresent() && "LOCKED".equals(p.get().getStatus().name())) {
                throw new IllegalStateException("El período " + orig.getPeriodEnd().getYear() + "-"
                        + String.format("%02d", orig.getPeriodEnd().getMonthValue())
                        + " está bloqueado definitivamente. Los ajustes deben hacerse como hechos del período actual con notas explicativas (NIC 8 — corrección de errores).");
            }
        }
        User u = userUtil.getUser();
        Long rootId = orig.getSesionOrigenId() != null ? orig.getSesionOrigenId() : orig.getId();
        SesionConciliacion nueva = SesionConciliacion.builder()
                .companyId(orig.getCompanyId()).bankAccountId(orig.getBankAccountId())
                .periodStart(orig.getPeriodStart()).periodEnd(orig.getPeriodEnd())
                .estado("REABIERTA").version(orig.getVersion() + 1).sesionOrigenId(rootId)
                .saldoExtracto(orig.getSaldoExtracto()).saldoLibros(orig.getSaldoLibros()).diferencia(orig.getDiferencia())
                .hashExtracto(orig.getHashExtracto())
                .createdBy(u != null ? u.getId() : null)
                .notas("Reabierta desde sesión #" + orig.getId() + ". Motivo: " + motivo.trim()
                        + (evidenciaFileName != null && !evidenciaFileName.isBlank() ? " | evidencia: " + evidenciaFileName : ""))
                .build();
        nueva = sesionRepo.save(nueva);
        auditPublisher.publish(AuditAction.CREATE, AuditModule.BNK, AuditSeverity.HIGH,
                "SesionConciliacion", nueva.getId(),
                "Reapertura directa: nueva versión v" + nueva.getVersion() + " desde sesión #" + orig.getId()
                        + " | motivo: " + motivo.trim(), null, null, null);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("nuevaSesionId", nueva.getId());
        r.put("version", nueva.getVersion());
        r.put("estado", "REABIERTA");
        return r;
    }

    /** BNK-HU-075 E3/E4/E5: aprobar reapertura (segregación + "REABRIR" + 2ª firma + nueva versión). */
    @Transactional
    public Map<String, Object> aprobarReapertura(Long solicitudId, String confirmText, String documento, String tp) {
        SolicitudReapertura sol = solicitudRepo.findByIdAndDeletedAtIsNull(solicitudId)
                .orElseThrow(() -> new IllegalArgumentException("Solicitud no encontrada."));
        if (!"PENDIENTE".equals(sol.getEstado()))
            throw new IllegalStateException("La solicitud no está PENDIENTE (actual: " + sol.getEstado() + ").");
        User u = userUtil.getUser();
        // HU-067 E2 / HU-075 E3: el solicitante no puede aprobar su propia solicitud
        if (sol.getSolicitanteId() != null && sol.getSolicitanteId().equals(u.getId())) {
            auditReject("SolicitudReapertura", solicitudId, "El solicitante no puede autorizar su propia reapertura");
            throw new IllegalStateException("BNK-CON-025: El solicitante de la reapertura no puede ser quien la autoriza.");
        }
        if (!hasAnyRole(u, REVIEWER_ROLES)) {
            auditReject("SolicitudReapertura", solicitudId, "Rol no autorizado para aprobar reapertura");
            throw new IllegalStateException("BNK-CON-013: solo un REVISOR_FISCAL/SUPERVISOR puede aprobar la reapertura.");
        }
        // HU-075 E4: confirmación reforzada
        if (!"REABRIR".equals(confirmText))
            throw new IllegalArgumentException("Debe escribir REABRIR (en mayúsculas) para confirmar la reapertura.");
        // HU-075 E4: 2ª firma electrónica del aprobador (stand-in: registro firmado con la confirmación REABRIR)
        SesionConciliacion orig = load(sol.getSesionId());
        String payload = "{\"accion\":\"REAPERTURA\",\"solicitud\":" + solicitudId + ",\"sesion\":" + orig.getId()
                + ",\"aprobador\":" + u.getId() + ",\"fecha\":\"" + LocalDateTime.now() + "\"}";
        firmaRepo.save(FirmaElectronica.builder()
                .companyId(orig.getCompanyId()).sesionId(orig.getId()).rolFirma("REAPERTURA")
                .firmanteUserId(u.getId()).firmanteNombre((nz(u.getName()) + " " + nz(u.getLastname())).trim())
                .firmanteDocumento(documento).firmanteTp(tp).firmanteRol("REVISOR_FISCAL")
                .metodoFirma("OTP").payloadFirma(payload).hashDocumento(signatureService.sha256Hex(payload))
                .selloTiempo(LocalDateTime.now()).build());
        // HU-075 E5: nueva versión preservando la anterior INTACTA
        Long rootId = orig.getSesionOrigenId() != null ? orig.getSesionOrigenId() : orig.getId();
        SesionConciliacion nueva = SesionConciliacion.builder()
                .companyId(orig.getCompanyId()).bankAccountId(orig.getBankAccountId())
                .periodStart(orig.getPeriodStart()).periodEnd(orig.getPeriodEnd())
                .estado("REABIERTA").version(orig.getVersion() + 1).sesionOrigenId(rootId)
                .saldoExtracto(orig.getSaldoExtracto()).saldoLibros(orig.getSaldoLibros()).diferencia(orig.getDiferencia())
                .hashExtracto(orig.getHashExtracto())
                .createdBy(u.getId())
                .notas("Reabierta desde sesión #" + orig.getId() + " (solicitud #" + solicitudId + ").")
                .build();
        nueva = sesionRepo.save(nueva);
        sol.setEstado("APROBADA"); sol.setAprobadorId(u.getId()); sol.setAprobadaAt(LocalDateTime.now());
        sol.setNuevaSesionId(nueva.getId());
        solicitudRepo.save(sol);
        auditPublisher.publish(AuditAction.CREATE, AuditModule.BNK, AuditSeverity.HIGH,
                "SesionConciliacion", nueva.getId(), "Reapertura aprobada: nueva versión v" + nueva.getVersion()
                        + " desde sesión #" + orig.getId() + " (solicitud #" + solicitudId + ")", null, payload, null);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("nuevaSesionId", nueva.getId()); r.put("version", nueva.getVersion()); r.put("estado", "REABIERTA");
        return r;
    }

    @Transactional
    public Map<String, Object> rechazarReapertura(Long solicitudId, String motivo) {
        SolicitudReapertura sol = solicitudRepo.findByIdAndDeletedAtIsNull(solicitudId)
                .orElseThrow(() -> new IllegalArgumentException("Solicitud no encontrada."));
        if (!"PENDIENTE".equals(sol.getEstado()))
            throw new IllegalStateException("La solicitud no está PENDIENTE.");
        User u = userUtil.getUser();
        if (sol.getSolicitanteId() != null && sol.getSolicitanteId().equals(u.getId()))
            throw new IllegalStateException("BNK-CON-025: El solicitante no puede decidir su propia solicitud.");
        sol.setEstado("RECHAZADA"); sol.setAprobadorId(u.getId()); sol.setAprobadaAt(LocalDateTime.now());
        sol.setMotivoRechazo(motivo);
        solicitudRepo.save(sol);
        auditPublisher.publishUpdate(AuditModule.BNK, "SolicitudReapertura", solicitudId, "Solicitud de reapertura RECHAZADA");
        Map<String, Object> r = new LinkedHashMap<>(); r.put("estado", "RECHAZADA"); return r;
    }

    /** BNK-HU-075 E8: histórico de versiones de la conciliación. */
    public List<Map<String, Object>> historial(Long sesionId) {
        SesionConciliacion s = load(sesionId);
        Long rootId = s.getSesionOrigenId() != null ? s.getSesionOrigenId() : s.getId();
        List<SesionConciliacion> versiones = new ArrayList<>();
        sesionRepo.findByIdAndDeletedAtIsNull(rootId).ifPresent(versiones::add);
        versiones.addAll(sesionRepo.findBySesionOrigenIdAndDeletedAtIsNullOrderByVersionAsc(rootId));
        versiones.sort(Comparator.comparingInt(SesionConciliacion::getVersion).reversed());
        return versiones.stream().map(v -> {
            Map<String, Object> m = row(v);
            m.put("esActual", "CERRADA".equals(v.getEstado()) || "REABIERTA".equals(v.getEstado()) || "BORRADOR".equals(v.getEstado()));
            return m;
        }).toList();
    }

    // ===================== archivado (Sec 12: soft-delete a 1 año) =====================

    /** Sec 12: lista de conciliaciones CERRADAS activas de la cuenta (visor "Conciliaciones cerradas"). */
    public List<Map<String, Object>> listCerradas(Long bankAccountId) {
        return sesionRepo.findByBankAccountIdAndDeletedAtIsNullOrderByIdDesc(bankAccountId).stream()
                .filter(s -> "CERRADA".equals(s.getEstado()))
                .map(s -> {
                    Map<String, Object> m = row(s);
                    boolean archivable = s.getCerradaAt() != null && s.getCerradaAt().isBefore(LocalDateTime.now().minusYears(1));
                    m.put("archivable", archivable);
                    return m;
                }).toList();
    }

    /** Sec 12: conciliaciones ya archivadas (read-only; el PDF sigue descargable). */
    public List<Map<String, Object>> listArchivadas(Long bankAccountId) {
        return sesionRepo.findByBankAccountIdAndDeletedAtIsNotNullOrderByIdDesc(bankAccountId).stream()
                .map(s -> {
                    Map<String, Object> m = row(s);
                    m.put("archivadaAt", s.getDeletedAt() != null ? s.getDeletedAt().toString() : null);
                    return m;
                }).toList();
    }

    /**
     * Sec 12: archivar (soft-delete) una conciliación CERRADA con más de 1 año.
     * NO destruye nada: el informe PDF y la evidencia se conservan en archivos_soporte
     * (retención 10 años, HU-062/063). Solo la oculta del listado activo.
     */
    @Transactional
    public Map<String, Object> archivar(Long sesionId) {
        SesionConciliacion s = load(sesionId);
        if (!"CERRADA".equals(s.getEstado()))
            throw new IllegalStateException("Solo se puede archivar una conciliación CERRADA (actual: " + s.getEstado() + ").");
        if (s.getCerradaAt() == null || !s.getCerradaAt().isBefore(LocalDateTime.now().minusYears(1)))
            throw new IllegalStateException("Solo se archivan conciliaciones cerradas hace más de 1 año. "
                    + "El informe PDF y la evidencia permanecen en Soportes (retención 10 años).");
        s.setDeletedAt(LocalDateTime.now());
        sesionRepo.save(s);
        auditPublisher.publishUpdate(AuditModule.BNK, "SesionConciliacion", sesionId,
                "Conciliación archivada (Sec 12, soft-delete a 1 año). Informe y evidencia conservados en Soportes.");
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", sesionId); r.put("archivada", true);
        return r;
    }

    /** BNK-HU-077 E8: descarga del informe PDF firmado + auditoría EXPORTAR. */
    public com.sigcon.backend.banks.archivos_soporte.domain.model.ArchivoSoporte downloadInforme(Long sesionId) {
        // loadAnyState: una conciliación archivada (deletedAt != null) sigue pudiendo
        // descargar su informe (el PDF está bajo retención 10 años). El @Filter de tenant
        // y el @PostLoad de la entidad siguen garantizando el aislamiento por empresa.
        SesionConciliacion s = loadAnyState(sesionId);
        if (s.getInformeArchivoId() == null)
            throw new IllegalStateException("La sesión aún no tiene informe generado (debe estar CERRADA).");
        var archivo = pdfService.fetchInforme(s.getInformeArchivoId());
        auditPublisher.publish(AuditAction.EXPORT, AuditModule.BNK, AuditSeverity.LOW,
                "SesionConciliacion", sesionId, "Descarga informe conciliación PDF archivo=" + archivo.getId(), null, null, null);
        return archivo;
    }

    /** BNK-HU-066 E6: verificación de firmas de la sesión. */
    public Map<String, Object> verificarFirma(Long sesionId) {
        load(sesionId); // valida tenant + existencia
        return signatureService.verify(sesionId);
    }

    /** BNK-HU-075 E1: solicitudes de la sesión. */
    public List<SolicitudReapertura> solicitudes(Long sesionId) {
        return solicitudRepo.findBySesionIdAndDeletedAtIsNullOrderByIdDesc(sesionId);
    }

    /** BNK-HU-075 E6: sugerir reverso de los asientos de ajuste APROBADOS de la versión previa. */
    public List<Map<String, Object>> sugerirReverso(Long sesionId) {
        SesionConciliacion s = load(sesionId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (PartidaConciliatoria p : partidaRepo.findByBankAccountIdAndDeletedAtIsNullOrderByIdDesc(s.getBankAccountId())) {
            if (p.getComprobanteAjusteId() == null) continue;
            journalRepo.findById(p.getComprobanteAjusteId()).ifPresent(je -> {
                if ("POSTED".equals(je.getStatus().name())) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("comprobanteId", je.getId());
                    m.put("partidaId", p.getId());
                    m.put("tipo", p.getTipo());
                    m.put("monto", p.getMonto());
                    m.put("nota", "Comprobante APROBADO en CG. El reverso debe ejecutarse manualmente desde Contabilidad General.");
                    out.add(m);
                }
            });
        }
        return out;
    }

    // ===================== helpers =====================

    private void checkSegregation(SesionConciliacion s, User u, String accion) {
        boolean flexible = Boolean.TRUE.equals(s.getModoFlexible());
        boolean sameCreator = s.getCreatedBy() != null && s.getCreatedBy().equals(u.getId());
        boolean sameSender = s.getEnviadaRevisionBy() != null && s.getEnviadaRevisionBy().equals(u.getId());
        if (!hasAnyRole(u, REVIEWER_ROLES)) {
            auditReject("SesionConciliacion", s.getId(), "Rol no autorizado para " + accion + " (requiere REVISOR_FISCAL/SUPERVISOR_CONCILIACION)");
            throw new IllegalStateException("BNK-CON-013: su rol no está autorizado para aprobar la conciliación (requiere REVISOR_FISCAL o SUPERVISOR_CONCILIACION).");
        }
        if (!flexible && (sameCreator || sameSender)) {
            String cual = sameCreator ? "quien elaboró la conciliación" : "quien la envió a revisión";
            auditReject("SesionConciliacion", s.getId(), "El aprobador es " + cual + " (segregación)");
            throw new IllegalStateException("BNK-CON-013: el aprobador no puede ser " + cual
                    + ". La segregación de funciones exige un revisor distinto.");
        }
        if (flexible && (sameCreator || sameSender)) {
            // HU-067 E5: modo flexible exige doble firma + motivo de excepción >=100
            if (s.getFirmaElaboradorId() == null || s.getFirmaRevisorId() == null
                    || s.getMotivoExcepcion() == null || s.getMotivoExcepcion().trim().length() < 100) {
                throw new IllegalStateException("Modo flexible: para que el mismo usuario apruebe se exige doble firma (OTP + biométrica) "
                        + "y un motivo de excepción de mínimo 100 caracteres.");
            }
        }
    }

    private void auditReject(String entity, Long id, String detalle) {
        // HU-067 E4: el rechazo debe quedar registrado AUNQUE la transacción de la
        // acción haga rollback al lanzar la excepción. Por eso se usa AuditLogService
        // .register (REQUIRES_NEW), que commitea en su propia transacción ANTES del
        // throw; un auditPublisher.publish (AFTER_COMMIT) se descartaría con el rollback.
        try {
            auditLogService.register(AuditAction.UPDATE, AuditModule.BNK, AuditSeverity.HIGH,
                    entity, id, "RECHAZO_PERMISO: Segregación de funciones violada - " + detalle,
                    null, "{\"resultado\":\"RECHAZO_PERMISO\",\"motivo\":\"Segregación de funciones violada\"}", null);
        } catch (Exception ignore) { }
    }

    private boolean hasAnyRole(User u, Set<String> roles) {
        return u != null && u.getRoles() != null && u.getRoles().stream().anyMatch(r -> roles.contains(r.getName()));
    }

    private String buildSignaturePayload(SesionConciliacion s, User u, String rolFirma, String documento, String tp) {
        BankAccount ba = bankAccountRepo.findById(s.getBankAccountId()).orElse(null);
        String cuentaMask = ba != null && ba.getAccountNumber() != null && ba.getAccountNumber().length() > 4
                ? "****" + ba.getAccountNumber().substring(ba.getAccountNumber().length() - 4) : "****";
        long pendientes = partidaRepo.findByBankAccountIdAndEstadoAndDeletedAtIsNullOrderByIdDesc(s.getBankAccountId(), "PENDIENTE").size();
        long totalPartidas = partidaRepo.findByBankAccountIdAndDeletedAtIsNullOrderByIdDesc(s.getBankAccountId()).size();
        long conciliados = movementRepo.findAllByBankAccountIdOrdered(s.getBankAccountId()).stream()
                .filter(m -> "CONCILIADO".equals(m.getEstadoConciliacionSesion())).count();
        Map<String, Object> firmante = new LinkedHashMap<>();
        firmante.put("id", u != null ? u.getId() : null);
        firmante.put("nombre", u != null ? (nz(u.getName()) + " " + nz(u.getLastname())).trim() : null);
        firmante.put("documento", documento);
        firmante.put("rol", rolFirma);
        firmante.put("tarjeta_profesional", tp);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tipo_documento", "CONCILIACION");
        payload.put("sesion_id", s.getId());
        payload.put("cuenta_enmascarada", cuentaMask);
        payload.put("periodo", String.valueOf(s.getPeriodStart()) + "/" + s.getPeriodEnd());
        // Bug 6: el informe firmado lleva los saldos recalculados por sesión.
        BigDecimal[] saldosFirma = computeSaldos(s);
        payload.put("saldo_extracto", saldosFirma[0]);
        payload.put("saldo_libros", saldosFirma[1]);
        payload.put("diferencia", saldosFirma[2]);
        payload.put("total_partidas", totalPartidas);
        payload.put("partidas_pendientes", pendientes);
        payload.put("movimientos_conciliados", conciliados);
        payload.put("hash_extracto", s.getHashExtracto());
        payload.put("fecha_firma", LocalDateTime.now().atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        payload.put("firmante", firmante);
        return signatureService.canonicalJson(payload);
    }

    private SesionConciliacion load(Long id) {
        return sesionRepo.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Sesión de conciliación no encontrada: " + id));
    }

    /** Carga una sesión sin filtrar por deletedAt (para leer/descargar archivadas). Tenant-safe vía @Filter + @PostLoad. */
    private SesionConciliacion loadAnyState(Long id) {
        return sesionRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Sesión de conciliación no encontrada: " + id));
    }

    /**
     * QA Conciliación (2026-05-25) Bug 6: recalcula los saldos de la sesión a partir de
     * SUS movimientos, no del valor almacenado (que se fijó una sola vez con TODOS los
     * movimientos de la cuenta, dando el mismo $11M en todas las sesiones).
     *   [0] saldoExtracto = suma de importes del extracto asignado a la sesión.
     *   [1] saldoLibros   = suma de importes de los libros (MANUAL) del período.
     *   [2] diferencia    = saldoExtracto - saldoLibros.
     */
    private BigDecimal[] computeSaldos(SesionConciliacion s) {
        BigDecimal ext = movementRepo.findBySesionConciliacionIdOrderByMovementDateAscIdAsc(s.getId())
                .stream().map(m -> m.getAmount() == null ? BigDecimal.ZERO : m.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal lib = movementRepo.findByBankAccountSourceTypeAndPeriod(
                        s.getBankAccountId(), FinancialMovementSourceType.MANUAL,
                        s.getPeriodStart(), s.getPeriodEnd())
                .stream().map(m -> m.getAmount() == null ? BigDecimal.ZERO : m.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new BigDecimal[]{ ext, lib, ext.subtract(lib) };
    }

    private Map<String, Object> row(SesionConciliacion s) {
        BigDecimal[] saldos = computeSaldos(s);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("bankAccountId", s.getBankAccountId());
        m.put("periodStart", s.getPeriodStart());
        m.put("periodEnd", s.getPeriodEnd());
        m.put("estado", s.getEstado());
        m.put("version", s.getVersion());
        m.put("sesionOrigenId", s.getSesionOrigenId());
        m.put("saldoExtracto", saldos[0]);
        m.put("saldoLibros", saldos[1]);
        m.put("diferencia", saldos[2]);
        m.put("createdBy", s.getCreatedBy());
        m.put("enviadaRevisionBy", s.getEnviadaRevisionBy());
        m.put("aprobadaBy", s.getAprobadaBy());
        m.put("cerradaAt", s.getCerradaAt() != null ? s.getCerradaAt().toString() : null);
        m.put("firmaElaboradorId", s.getFirmaElaboradorId());
        m.put("firmaRevisorId", s.getFirmaRevisorId());
        m.put("informeArchivoId", s.getInformeArchivoId());
        m.put("modoFlexible", s.getModoFlexible());
        return m;
    }

    private String nz(String s) { return s == null ? "" : s; }
}
