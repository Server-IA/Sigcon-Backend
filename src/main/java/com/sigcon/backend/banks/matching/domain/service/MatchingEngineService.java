package com.sigcon.backend.banks.matching.domain.service;

import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;
import com.sigcon.backend.banks.bankaccounts.domain.model.BankAccount;
import com.sigcon.backend.banks.bankaccounts.domain.repository.BankAccountRepository;
import com.sigcon.backend.banks.financialmovements.domain.model.FinancialMovement;
import com.sigcon.backend.banks.financialmovements.domain.model.enums.FinancialMovementSourceType;
import com.sigcon.backend.banks.financialmovements.domain.repository.FinancialMovementRepository;
import com.sigcon.backend.banks.matching.domain.model.Emparejamiento;
import com.sigcon.backend.banks.matching.domain.model.EmparejamientoDetalle;
import com.sigcon.backend.banks.matching.domain.model.ParametrosMatching;
import com.sigcon.backend.banks.matching.domain.model.SesionConciliacion;
import com.sigcon.backend.banks.matching.domain.repository.EmparejamientoDetalleRepository;
import com.sigcon.backend.banks.matching.domain.repository.EmparejamientoRepository;
import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.utils.UserUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * BNK-HU-069: motor de matching en cinco fases de confianza decreciente.
 *
 * <p>Mapeo de modelo (R5): el lado "extracto" son {@link FinancialMovement} con
 * {@code source_type = BANK_IMPORT} y el lado "libros" son movimientos con
 * {@code source_type = MANUAL} de la misma cuenta. El resultado de cada
 * comparación se persiste en {@link Emparejamiento} + {@link EmparejamientoDetalle}
 * (soporta 1:1, N:1, 1:N, N:M).</p>
 *
 * <p>Fases: 1) exacto (score 100), 2) alto (85-95), 3) medio (60-84 + ambigüedad),
 * 4) agregado N:1/1:N (score 75). El score 0-100 se calcula con pesos
 * configurables (HU-072). Los emparejamientos con score >= umbral_auto_aprobar se
 * confirman y dejan los movimientos en CONCILIADO; entre umbral_sugerir y
 * auto_aprobar-1 quedan como PROPUESTO (EN_REVISION); los ambiguos como AMBIGUO.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingEngineService {

    // tipo_emparejamiento
    private static final String T_UNO_A_UNO = "UNO_A_UNO";
    private static final String T_N_A_UNO = "N_A_UNO";
    private static final String T_UNO_A_N = "UNO_A_N";
    // metodo
    private static final String M_EXACTO = "AUTOMATICO_EXACTO";
    private static final String M_ALTO = "AUTOMATICO_ALTO";
    private static final String M_MEDIO = "AUTOMATICO_MEDIO";
    private static final String M_AGREGADO = "AUTOMATICO_AGREGADO";
    // estado emparejamiento
    private static final String E_CONFIRMADO = "CONFIRMADO";
    private static final String E_PROPUESTO = "PROPUESTO";
    private static final String E_AMBIGUO = "AMBIGUO";
    private static final String E_DESHECHO = "DESHECHO";
    // estado_conciliacion del movimiento
    private static final String C_NO = "NO_CONCILIADO";
    private static final String C_REV = "EN_REVISION";
    private static final String C_OK = "CONCILIADO";

    private static final BigDecimal UMBRAL_AGREGADO_DEFAULT = new BigDecimal("1000000");
    private static final int MAX_SUBSET = 8;
    private static final int MAX_POOL_AGREGADO = 20;

    /**
     * QA Bloque BNK (2026-06-03) Bug 1: un movimiento es candidato si NO está conciliado
     * ni en el estado OFICIAL (committed por una sesión cerrada anterior) ni en el estado
     * de TRABAJO de la sesión actual. El matching ya NO mira el estado oficial como bandera
     * de trabajo: usa `estadoConciliacionSesion`.
     */
    private boolean disponible(FinancialMovement m) {
        return !C_OK.equals(m.getEstadoConciliacion()) && !C_OK.equals(m.getEstadoConciliacionSesion());
    }
    private static final int VENTANA_AGREGADO_DIAS = 5;

    private final FinancialMovementRepository movementRepository;
    private final EmparejamientoRepository emparejamientoRepository;
    private final EmparejamientoDetalleRepository detalleRepository;
    private final ParametrosMatchingService parametrosMatchingService;
    private final BankAccountRepository bankAccountRepository;
    private final com.sigcon.backend.banks.matching.domain.repository.SesionConciliacionRepository sesionConciliacionRepository;
    private final AuditPublisher auditPublisher;
    private final UserUtil userUtil;

    /**
     * BNK-HU-069 E1..E10: ejecuta el motor sobre los movimientos NO conciliados de
     * la cuenta. Re-ejecutable: no toca emparejamientos ya CONFIRMADOS (E9).
     */
    @Transactional
    public Map<String, Object> runEngine(Long bankAccountId) {
        User user = userUtil.getUser();
        BankAccount bankAccount = bankAccountRepository.findById(bankAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Cuenta bancaria no encontrada."));
        ParametrosMatching p = parametrosMatchingService.getEffective(bankAccountId);
        // E9: re-ejecución limpia lo NO confirmado y vuelve esos movimientos a NO_CONCILIADO.
        resetNonConfirmed(bankAccountId);
        // E1: candidatos. extracto = BANK_IMPORT no conciliado; libros = MANUAL no conciliado.
        List<FinancialMovement> extPool = new ArrayList<>(movementRepository
                .findByBankAccount_IdAndSourceType(bankAccountId, FinancialMovementSourceType.BANK_IMPORT)
                .stream().filter(this::disponible).toList());
        List<FinancialMovement> libPool = new ArrayList<>(movementRepository
                .findByBankAccount_IdAndSourceType(bankAccountId, FinancialMovementSourceType.MANUAL)
                .stream().filter(this::disponible).toList());
        return runEngineCore(bankAccount, extPool, libPool, p, user, null);
    }

    /**
     * Conciliación Paso 4: ejecuta el motor ACOTADO a una sesión. Extracto = movimientos
     * importados bajo la sesión (sesion_conciliacion_id); libros = MANUAL dentro del período.
     * Reutiliza el mismo núcleo de 5 fases que la corrida por cuenta.
     */
    @Transactional
    public Map<String, Object> runEngineForSession(Long sesionId) {
        User user = userUtil.getUser();
        SesionConciliacion s = sesionConciliacionRepository.findById(sesionId)
                .orElseThrow(() -> new IllegalArgumentException("Sesión de conciliación no encontrada."));
        BankAccount bankAccount = bankAccountRepository.findById(s.getBankAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Cuenta bancaria no encontrada."));
        ParametrosMatching p = parametrosMatchingService.getEffective(bankAccount.getId());
        resetNonConfirmed(bankAccount.getId());
        List<FinancialMovement> extPool = new ArrayList<>(movementRepository
                .findBySesionConciliacionIdOrderByMovementDateAscIdAsc(sesionId)
                .stream().filter(m -> FinancialMovementSourceType.BANK_IMPORT.equals(m.getSourceType())
                        && disponible(m)).toList());
        List<FinancialMovement> libPool = new ArrayList<>(movementRepository
                .findByBankAccountSourceTypeAndPeriod(bankAccount.getId(), FinancialMovementSourceType.MANUAL,
                        s.getPeriodStart(), s.getPeriodEnd())
                .stream().filter(this::disponible).toList());
        Map<String, Object> r = runEngineCore(bankAccount, extPool, libPool, p, user, sesionId);
        r.put("sesionConciliacionId", sesionId);
        return r;
    }

    /** Núcleo de las 5 fases (E2..E5) + score + resumen (E10), compartido por cuenta y por sesión. */
    private Map<String, Object> runEngineCore(BankAccount bankAccount, List<FinancialMovement> extPool,
                                              List<FinancialMovement> libPool, ParametrosMatching p, User user,
                                              Long sesionId) {
        long t0 = System.currentTimeMillis();
        Long bankAccountId = bankAccount.getId();
        int totalExtracto = extPool.size();
        int totalLibros = libPool.size();
        List<Emparejamiento> created = new ArrayList<>();

        // ---- FASE 1: match exacto (E2) — monto exacto + signo + (ref o cheque) idéntico ----
        for (FinancialMovement ext : new ArrayList<>(extPool)) {
            List<FinancialMovement> cands = libPool.stream()
                    .filter(lib -> amountWithinTol(ext, lib, p)
                            && sameSign(ext, lib)
                            && refOrChequeEqual(ext, lib))
                    .toList();
            if (cands.size() == 1) {
                FinancialMovement lib = cands.get(0);
                created.add(persist(bankAccount, List.of(ext), List.of(lib), T_UNO_A_UNO, M_EXACTO, 100, false, p, user, null));
                extPool.remove(ext);
                libPool.remove(lib);
            }
        }

        // ---- FASE 2: match alto (E3) — monto + |fecha|<=1 + (tercero o textsim>0.7), 1 candidato, score>=85 ----
        for (FinancialMovement ext : new ArrayList<>(extPool)) {
            List<FinancialMovement> cands = libPool.stream()
                    .filter(lib -> amountWithinTol(ext, lib, p)
                            && dayDiff(ext, lib) <= 1
                            && (nitEquals(ext, lib) || textSim(ext, lib) > 0.7))
                    .toList();
            if (cands.size() == 1) {
                FinancialMovement lib = cands.get(0);
                int score = scoreFunction(ext, lib, p);
                if (score >= 85) {
                    created.add(persist(bankAccount, List.of(ext), List.of(lib), T_UNO_A_UNO, M_ALTO, score, false, p, user, null));
                    extPool.remove(ext);
                    libPool.remove(lib);
                }
            }
        }

        // ---- FASE 3: match medio (E4) — tolerancias + score, detección de ambigüedad ----
        for (FinancialMovement ext : new ArrayList<>(extPool)) {
            List<FinancialMovement> cands = libPool.stream()
                    .filter(lib -> amountWithinTol(ext, lib, p) && dayDiff(ext, lib) <= p.getToleranciaFechaDias())
                    .toList();
            if (cands.isEmpty()) continue;
            // score por candidato, ordenado desc
            List<Map.Entry<FinancialMovement, Integer>> scored = cands.stream()
                    .map(lib -> Map.entry(lib, scoreFunction(ext, lib, p)))
                    .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                    .toList();
            int bestScore = scored.get(0).getValue();
            if (bestScore < p.getUmbralScoreSugerir()) continue; // queda sin match
            FinancialMovement best = scored.get(0).getKey();
            // ¿otros candidatos a menos de ±5 puntos del mejor?
            boolean ambiguo = scored.stream().skip(1).anyMatch(e -> bestScore - e.getValue() <= 5);
            String metodo = ambiguo ? M_MEDIO : M_MEDIO;
            created.add(persist(bankAccount, List.of(ext), List.of(best), T_UNO_A_UNO, metodo, bestScore, ambiguo, p, user, null));
            extPool.remove(ext);
            libPool.remove(best);
        }

        // ---- FASE 4: agregado N:1 y 1:N (E5) — solo si permitir_n_a_m ----
        if (Boolean.TRUE.equals(p.getPermitirNaM())) {
            BigDecimal umbralAgg = UMBRAL_AGREGADO_DEFAULT;
            // N libros = 1 extracto
            for (FinancialMovement ext : new ArrayList<>(extPool)) {
                if (ext.getAmount() == null || ext.getAmount().abs().compareTo(umbralAgg) <= 0) continue;
                List<FinancialMovement> pool = subsetPool(libPool, ext, p);
                List<FinancialMovement> subset = findUniqueSubset(pool, ext.getAmount().abs(), p);
                if (subset != null) {
                    created.add(persist(bankAccount, List.of(ext), subset, T_N_A_UNO, M_AGREGADO, 75, false, p, user, null));
                    extPool.remove(ext);
                    libPool.removeAll(subset);
                }
            }
            // 1 libro = N extracto
            for (FinancialMovement lib : new ArrayList<>(libPool)) {
                if (lib.getAmount() == null || lib.getAmount().abs().compareTo(umbralAgg) <= 0) continue;
                List<FinancialMovement> pool = subsetPool(extPool, lib, p);
                List<FinancialMovement> subset = findUniqueSubset(pool, lib.getAmount().abs(), p);
                if (subset != null) {
                    created.add(persist(bankAccount, subset, List.of(lib), T_UNO_A_N, M_AGREGADO, 75, false, p, user, null));
                    libPool.remove(lib);
                    extPool.removeAll(subset);
                }
            }
        }

        // QA Conciliación (2026-05-25) Bug 1: marcar cada emparejamiento con la sesión
        // de la corrida, para que el Paso 5 los liste acotados a la sesión (y no a toda
        // la cuenta). En la corrida por cuenta (sesionId == null) no se marca.
        if (sesionId != null) {
            for (Emparejamiento e : created) {
                e.setReconciliationSessionId(sesionId);
                emparejamientoRepository.save(e);
            }
        }

        // ---- E10: resumen ----
        long conciliados = created.stream().filter(e -> E_CONFIRMADO.equals(e.getEstado())).count();
        long sugeridos = created.stream().filter(e -> E_PROPUESTO.equals(e.getEstado())).count();
        long ambiguos = created.stream().filter(e -> E_AMBIGUO.equals(e.getEstado())).count();
        double segundos = (System.currentTimeMillis() - t0) / 1000.0;

        String paramsSnapshot = String.format(
                "tolAbs=%s,tolPct=%s,tolDias=%d,auto=%d,sugerir=%d,pesos=%d/%d/%d/%d,permitirNaM=%s",
                p.getToleranciaMontoAbs(), p.getToleranciaMontoPct(), p.getToleranciaFechaDias(),
                p.getUmbralScoreAutoAprobar(), p.getUmbralScoreSugerir(),
                p.getPesoMonto(), p.getPesoFecha(), p.getPesoTexto(), p.getPesoReferencia(), p.getPermitirNaM());

        // E9: registrar la corrida en auditoría con los parámetros usados.
        auditPublisher.publishUpdate(AuditModule.BNK, "Emparejamiento", bankAccountId,
                "MOTOR MATCHING cuenta=" + bankAccountId + " | params={" + paramsSnapshot + "}"
                        + " | conciliados=" + conciliados + " sugeridos=" + sugeridos
                        + " ambiguos=" + ambiguos + " sinMatchExtracto=" + extPool.size()
                        + " librosSinPareja=" + libPool.size());

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("cuentaBancariaId", bankAccountId);
        r.put("totalExtracto", totalExtracto);
        r.put("totalLibros", totalLibros);
        r.put("conciliadosAutomaticamente", conciliados);
        r.put("sugeridos", sugeridos);
        r.put("ambiguos", ambiguos);
        r.put("sinMatchExtracto", extPool.size());
        r.put("librosSinPareja", libPool.size());
        r.put("tiempoSegundos", segundos);
        r.put("parametrosUsados", paramsSnapshot);
        r.put("emparejamientos", created.stream().map(this::toSummary).toList());
        return r;
    }

    // ---- persistencia de un emparejamiento + detalle + estado de movimientos ----

    private Emparejamiento persist(BankAccount ba, List<FinancialMovement> extSide, List<FinancialMovement> libSide,
                                   String tipo, String metodo, int score, boolean ambiguo,
                                   ParametrosMatching p, User user, String motivo) {
        BigDecimal sumaExt = extSide.stream().map(m -> m.getAmount().abs()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumaLib = libSide.stream().map(m -> m.getAmount().abs()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal dif = sumaExt.subtract(sumaLib).abs();

        // E7/E8: estado por score (salvo ambiguo, que siempre requiere decisión humana).
        String estado;
        String movEstado;
        boolean confirmar = false;
        if (ambiguo) {
            estado = E_AMBIGUO;
            movEstado = C_REV;
        } else if (score >= p.getUmbralScoreAutoAprobar()) {
            estado = E_CONFIRMADO;
            movEstado = C_OK;
            confirmar = true;
        } else {
            estado = E_PROPUESTO;
            movEstado = C_REV;
        }

        String paramsSnapshot = String.format("auto=%d,sugerir=%d,pesos=%d/%d/%d/%d",
                p.getUmbralScoreAutoAprobar(), p.getUmbralScoreSugerir(),
                p.getPesoMonto(), p.getPesoFecha(), p.getPesoTexto(), p.getPesoReferencia());

        Emparejamiento emp = Emparejamiento.builder()
                .companyId(ba.getCompanyId())
                .cuentaBancariaId(ba.getId())
                .tipoEmparejamiento(tipo)
                .metodo(metodo)
                .score(score)
                .estado(estado)
                .sumaExtracto(sumaExt)
                .sumaLibros(sumaLib)
                .diferencia(dif)
                .motivoMatchManual(motivo)
                .parametrosUsados(paramsSnapshot)
                .build();
        if (confirmar) {
            emp.setConfirmadoAt(java.time.LocalDateTime.now());
            emp.setConfirmadoBy(user != null ? user.getUsername() : "sistema");
        }
        emp = emparejamientoRepository.save(emp);

        for (FinancialMovement m : extSide) addDetalle(emp, m, "EXTRACTO", movEstado);
        for (FinancialMovement m : libSide) addDetalle(emp, m, "LIBROS", movEstado);
        return emp;
    }

    private void addDetalle(Emparejamiento emp, FinancialMovement m, String lado, String movEstado) {
        detalleRepository.save(EmparejamientoDetalle.builder()
                .companyId(emp.getCompanyId())
                .emparejamientoId(emp.getId())
                .financialMovementId(m.getId())
                .lado(lado)
                .monto(m.getAmount())
                .build());
        // Bug 1: estado de TRABAJO (no toca el oficial hasta el cierre).
        m.setEstadoConciliacionSesion(movEstado);
        movementRepository.save(m);
    }

    /** E9: deshace (soft) los emparejamientos NO confirmados y libera sus movimientos. */
    private void resetNonConfirmed(Long bankAccountId) {
        List<Emparejamiento> existing = emparejamientoRepository
                .findByCuentaBancariaIdAndDeletedAtIsNullOrderByIdDesc(bankAccountId);
        for (Emparejamiento emp : existing) {
            if (E_CONFIRMADO.equals(emp.getEstado())) continue;
            for (EmparejamientoDetalle det : detalleRepository.findByEmparejamientoId(emp.getId())) {
                movementRepository.findById(det.getFinancialMovementId()).ifPresent(m -> {
                    if (!C_OK.equals(m.getEstadoConciliacionSesion())) {
                        m.setEstadoConciliacionSesion(C_NO);
                        movementRepository.save(m);
                    }
                });
                detalleRepository.delete(det);
            }
            emp.setEstado(E_DESHECHO);
            emp.setDeletedAt(java.time.LocalDateTime.now());
            emparejamientoRepository.save(emp);
        }
    }

    // ---- HU-069 E6: función de score ponderado ----

    int scoreFunction(FinancialMovement ext, FinancialMovement lib, ParametrosMatching p) {
        BigDecimal extAbs = ext.getAmount().abs();
        BigDecimal libAbs = lib.getAmount().abs();
        if (!amountWithinTol(ext, lib, p)) return 0; // E6: monto fuera de tolerancia => 0
        BigDecimal amountDiff = extAbs.subtract(libAbs).abs();
        double factorMonto = extAbs.signum() == 0
                ? (amountDiff.signum() == 0 ? 1.0 : 0.0)
                : Math.max(0.0, 1.0 - amountDiff.doubleValue() / extAbs.doubleValue());
        int dd = dayDiff(ext, lib);
        int tolDias = Math.max(p.getToleranciaFechaDias() == null ? 0 : p.getToleranciaFechaDias(), 0);
        double factorFecha = Math.max(0.0, 1.0 - (double) dd / (tolDias + 1));
        double simTexto = textSim(ext, lib);
        double factorRef = refOrChequeEqual(ext, lib) ? 1.0 : 0.0;
        double bonusTercero = nitEquals(ext, lib) ? 10.0 : 0.0;
        double raw = p.getPesoMonto() * factorMonto + p.getPesoFecha() * factorFecha
                + p.getPesoTexto() * simTexto + p.getPesoReferencia() * factorRef + bonusTercero;
        return Math.max(0, (int) Math.round(Math.min(100.0, raw)));
    }

    // ---- helpers de comparación ----

    private boolean amountWithinTol(FinancialMovement ext, FinancialMovement lib, ParametrosMatching p) {
        if (ext.getAmount() == null || lib.getAmount() == null) return false;
        BigDecimal extAbs = ext.getAmount().abs();
        BigDecimal diff = extAbs.subtract(lib.getAmount().abs()).abs();
        if (diff.compareTo(p.getToleranciaMontoAbs()) <= 0) return true;
        if (p.getToleranciaMontoPct() != null && p.getToleranciaMontoPct().signum() > 0) {
            BigDecimal tolPct = extAbs.multiply(p.getToleranciaMontoPct()).divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
            return diff.compareTo(tolPct) <= 0;
        }
        return false;
    }

    private boolean sameSign(FinancialMovement a, FinancialMovement b) {
        return a.getAmount() != null && b.getAmount() != null && a.getAmount().signum() == b.getAmount().signum();
    }

    private boolean refOrChequeEqual(FinancialMovement a, FinancialMovement b) {
        if (notBlank(a.getExternalReference()) && a.getExternalReference().equalsIgnoreCase(b.getExternalReference())) return true;
        return notBlank(a.getNumeroCheque()) && a.getNumeroCheque().equalsIgnoreCase(b.getNumeroCheque());
    }

    private boolean nitEquals(FinancialMovement a, FinancialMovement b) {
        return notBlank(a.getNitDetectado()) && a.getNitDetectado().equals(b.getNitDetectado());
    }

    private int dayDiff(FinancialMovement a, FinancialMovement b) {
        LocalDate da = a.getMovementDate();
        LocalDate db = b.getMovementDate();
        if (da == null || db == null) return Integer.MAX_VALUE;
        return (int) Math.abs(ChronoUnit.DAYS.between(da, db));
    }

    private double textSim(FinancialMovement a, FinancialMovement b) {
        return textSim(a.getDescripcionNormalizada(), b.getDescripcionNormalizada());
    }

    /** Similitud de Jaccard por tokens de la descripción normalizada. */
    double textSim(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) return 0.0;
        Set<String> ta = new HashSet<>(Arrays.asList(a.trim().split("\\s+")));
        Set<String> tb = new HashSet<>(Arrays.asList(b.trim().split("\\s+")));
        if (ta.isEmpty() || tb.isEmpty()) return 0.0;
        Set<String> inter = new HashSet<>(ta);
        inter.retainAll(tb);
        Set<String> union = new HashSet<>(ta);
        union.addAll(tb);
        return (double) inter.size() / union.size();
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    // ---- subset-sum (Fase 4) ----

    /** Acota el pool al mismo signo y ventana ±5 días del objetivo, máximo MAX_POOL_AGREGADO. */
    private List<FinancialMovement> subsetPool(List<FinancialMovement> pool, FinancialMovement target, ParametrosMatching p) {
        return pool.stream()
                .filter(m -> m.getAmount() != null && m.getAmount().signum() == target.getAmount().signum())
                .filter(m -> dayDiff(m, target) <= VENTANA_AGREGADO_DIAS)
                .sorted((a, b) -> b.getAmount().abs().compareTo(a.getAmount().abs()))
                .limit(MAX_POOL_AGREGADO)
                .toList();
    }

    /**
     * Devuelve el ÚNICO subconjunto (>=2 elementos, máx {@link #MAX_SUBSET}) cuya suma
     * de valores absolutos coincide con {@code targetAbs} dentro de tolerancia. Si hay
     * 0 o más de 1 subconjunto, devuelve null (no es inequívoco).
     */
    private List<FinancialMovement> findUniqueSubset(List<FinancialMovement> pool, BigDecimal targetAbs, ParametrosMatching p) {
        List<List<FinancialMovement>> found = new ArrayList<>();
        dfsSubset(pool, 0, new ArrayList<>(), BigDecimal.ZERO, targetAbs, p, found);
        return found.size() == 1 ? found.get(0) : null;
    }

    private void dfsSubset(List<FinancialMovement> items, int idx, List<FinancialMovement> current, BigDecimal sum,
                           BigDecimal target, ParametrosMatching p, List<List<FinancialMovement>> found) {
        if (found.size() >= 2) return; // ya no es único, corta
        if (current.size() >= 2) {
            BigDecimal diff = sum.subtract(target).abs();
            boolean ok = diff.compareTo(p.getToleranciaMontoAbs()) <= 0
                    || (p.getToleranciaMontoPct() != null && p.getToleranciaMontoPct().signum() > 0
                        && diff.compareTo(target.multiply(p.getToleranciaMontoPct()).divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)) <= 0);
            if (ok) {
                found.add(new ArrayList<>(current));
                if (found.size() >= 2) return;
            }
        }
        if (current.size() >= MAX_SUBSET || idx >= items.size()) return;
        for (int i = idx; i < items.size(); i++) {
            current.add(items.get(i));
            dfsSubset(items, i + 1, current, sum.add(items.get(i).getAmount().abs()), target, p, found);
            current.remove(current.size() - 1);
            if (found.size() >= 2) return;
        }
    }

    private Map<String, Object> toSummary(Emparejamiento e) {
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
    }
}
