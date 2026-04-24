package com.sigcon.backend.assets.disposals.domain.service;

import com.sigcon.backend.assets.assets.domain.model.Assets;
import com.sigcon.backend.assets.assets.domain.model.enums.AssetStatus;
import com.sigcon.backend.assets.assets.domain.repository.AssetsRepository;
import com.sigcon.backend.assets.disposals.application.AssetDisposalDTO;
import com.sigcon.backend.assets.disposals.application.CreateDisposalRequest;
import com.sigcon.backend.assets.disposals.domain.model.AssetDisposal;
import com.sigcon.backend.assets.disposals.domain.model.enums.DisposalType;
import com.sigcon.backend.assets.disposals.domain.repository.AssetDisposalRepository;
import com.sigcon.backend.general.accounting.AccountingPeriodService;
import com.sigcon.backend.general.accounting.journal.application.CreateJournalEntryLineRequest;
import com.sigcon.backend.general.accounting.journal.application.CreateJournalEntryRequest;
import com.sigcon.backend.general.accounting.journal.application.JournalEntryDTO;
import com.sigcon.backend.general.accounting.journal.domain.model.enums.JournalSourceModule;
import com.sigcon.backend.general.accounting.journal.domain.repository.JournalEntryRepository;
import com.sigcon.backend.general.accounting.journal.domain.service.JournalEntryService;
import com.sigcon.backend.parametrization.account_mappings.domain.service.AccountMappingService;
import com.sigcon.backend.parametrization.account_mappings.domain.service.AccountingConcept;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Servicio de negocio para bajas y transferencias de activos fijos.
 * ACT-03: Gestiona el ciclo de vida de disposiciones, calcula
 * ganancia/perdida y genera asientos contables automaticamente.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssetDisposalService {

    private final AssetDisposalRepository disposalRepository;
    private final AssetsRepository assetsRepository;
    private final AccountingPeriodService accountingPeriodService;
    private final JournalEntryService journalEntryService;
    private final JournalEntryRepository journalEntryRepository;
    private final AccountMappingService accountMappingService;
    private final AuditPublisher auditPublisher;

    private final DataTableSpecificationBuilder<AssetDisposal> dataTableSpecificationBuilder =
            new DataTableSpecificationBuilder<>();

    // ───────────────────────────────────────────────────────────────
    // Listado paginado
    // ───────────────────────────────────────────────────────────────

    /**
     * Obtiene el listado paginado de disposiciones de activos aplicando
     * filtros dinamicos y ordenamiento segun DataTableRequest.
     *
     * @param request parametros de paginacion, filtros y ordenamiento
     * @return respuesta paginada con DTOs de disposicion
     */
    public DataTableResponse<AssetDisposalDTO> getDisposals(DataTableRequest request) {
        if (request == null) {
            request = new DataTableRequest();
        }

        int draw = Math.max(0, request.getDraw());
        int start = Math.max(0, request.getStart());
        int length = request.getLength();
        int safeLength = length <= 0 ? 20 : Math.min(length, 100);
        int page = start / safeLength;

        Sort sort = Sort.by("createdAt").descending();
        String orderCol = request.getOrderColumnName();
        if (orderCol != null && !orderCol.isBlank()) {
            try {
                sort = "desc".equalsIgnoreCase(request.getOrderDir())
                        ? Sort.by(orderCol).descending()
                        : Sort.by(orderCol).ascending();
            } catch (Exception ignored) {
                // Mantener orden por defecto si la columna no es valida
            }
        }

        Pageable pageable = length == -1
                ? Pageable.unpaged(sort)
                : PageRequest.of(page, safeLength, sort);

        Specification<AssetDisposal> specification = dataTableSpecificationBuilder.build(request);
        Page<AssetDisposal> disposalPage = disposalRepository.findAll(specification, pageable);

        return DataTableResponse.from(disposalPage.map(this::toDTO), draw);
    }

    // ───────────────────────────────────────────────────────────────
    // Creacion de disposicion
    // ───────────────────────────────────────────────────────────────

    /**
     * Registra una baja o transferencia de activo.
     * <ol>
     *   <li>Valida existencia del activo</li>
     *   <li>Valida que el activo este activo o en reparacion</li>
     *   <li>Valida periodo contable abierto</li>
     *   <li>Calcula ganancia/perdida</li>
     *   <li>Persiste la disposicion</li>
     *   <li>Actualiza estado del activo</li>
     *   <li>Intenta generar asiento contable</li>
     * </ol>
     *
     * @param request datos de la disposicion
     * @return respuesta estandar con el DTO de la disposicion creada
     */
    @Transactional
    public Object createDisposal(CreateDisposalRequest request) {

        // 1. Buscar activo
        Assets asset = assetsRepository.findById(request.getAssetId())
                .orElse(null);
        if (asset == null) {
            return ErrorRespondJson.getErrorRespondMessage(
                    Optional.of("DSP_001: El activo no fue encontrado."))
                    ;
        }

        // 2. Validar estado del activo
        if (asset.getStatus() != AssetStatus.ACTIVE && asset.getStatus() != AssetStatus.IN_REPAIR) {
            return ErrorRespondJson.getErrorRespondMessage(
                    Optional.of("DSP_002: El activo no se puede dar de baja porque no esta activo."))
                    ;
        }

        // ACT-03 E2: Validar que no tenga saldos pendientes (cuentas por pagar asociadas)
        if (asset.getAccountsPayableReferenceId() != null) {
            return ErrorRespondJson.getErrorRespondMessage(
                    Optional.of("No es posible procesar la baja o transferencia: el activo tiene saldos pendientes."));
        }

        // 3. Validar periodo contable abierto
        try {
            accountingPeriodService.validatePeriodOpen(request.getDisposalDate());
        } catch (Exception e) {
            return ErrorRespondJson.getErrorRespondMessage(
                    Optional.of("DSP_003: El periodo contable esta cerrado para la fecha indicada."))
                    ;
        }

        // 4. Validar monto de enajenacion para BAJA
        if (request.getDisposalType() == DisposalType.BAJA && request.getDisposalAmount() == null) {
            return ErrorRespondJson.getErrorRespondMessage(
                    Optional.of("DSP_004: Debe especificar el monto de enajenacion para bajas."))
                    ;
        }

        // 5. Calcular valor en libros y ganancia/perdida
        BigDecimal bookValue = asset.getCurrentBookValue() != null
                ? asset.getCurrentBookValue()
                : asset.getAcquisitionValue();

        BigDecimal gainLoss;
        BigDecimal disposalAmount;

        if (request.getDisposalType() == DisposalType.BAJA) {
            disposalAmount = request.getDisposalAmount();
            gainLoss = disposalAmount.subtract(bookValue);
        } else {
            // TRANSFERENCIA: sin monto de enajenacion, ganancia = 0
            disposalAmount = null;
            gainLoss = BigDecimal.ZERO;
        }

        // 6. Crear y persistir la disposicion
        AssetDisposal disposal = AssetDisposal.builder()
                .asset(asset)
                .disposalType(request.getDisposalType())
                .disposalDate(request.getDisposalDate())
                .disposalAmount(disposalAmount)
                .bookValueAtDisposal(bookValue)
                .gainLoss(gainLoss)
                .reason(request.getReason())
                .destinationInfo(request.getDestinationInfo())
                .build();

        disposal = disposalRepository.save(disposal);

        // 7. Actualizar estado del activo
        if (request.getDisposalType() == DisposalType.BAJA) {
            asset.setStatus(AssetStatus.DECOMMISSIONED);
        } else {
            asset.setStatus(AssetStatus.TRANSFERRED);
        }
        assetsRepository.save(asset);

        // 8. Intentar crear asiento contable (no falla si no se puede)
        try {
            Long journalEntryId = createJournalEntry(disposal, asset, bookValue, gainLoss);
            if (journalEntryId != null) {
                disposal.setJournalEntryId(journalEntryId);
                disposalRepository.save(disposal);
            }
        } catch (Exception e) {
            log.warn("No se pudo generar el asiento contable para la disposicion {}: {}",
                    disposal.getId(), e.getMessage());
        }

        auditPublisher.publishCreate(AuditModule.ACT, "AssetDisposal", disposal.getId(),
                "Disposicion de activo: " + asset.getAssetCode()
                        + " tipo=" + request.getDisposalType()
                        + " ganancia/perdida=" + gainLoss);

        return SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Disposicion registrada correctamente."),
                Optional.of(toDTO(disposal)));
    }

    // ───────────────────────────────────────────────────────────────
    // Consulta individual
    // ───────────────────────────────────────────────────────────────

    /**
     * Obtiene el detalle de una disposicion por su identificador.
     *
     * @param id identificador de la disposicion
     * @return respuesta estandar con el DTO o error si no existe
     */
    public Object getDisposal(Long id) {
        AssetDisposal disposal = disposalRepository.findById(id).orElse(null);
        if (disposal == null) {
            return ErrorRespondJson.getErrorRespondMessage(
                    Optional.of("La disposicion no fue encontrada."))
                    ;
        }
        return SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Disposicion obtenida correctamente."),
                Optional.of(toDTO(disposal)));
    }

    // ───────────────────────────────────────────────────────────────
    // Generacion de asiento contable
    // ───────────────────────────────────────────────────────────────

    /**
     * Crea el asiento contable para una baja de activo.
     * Para TRANSFERENCIA no se genera asiento (retorna null).
     * <p>
     * Estructura del asiento para BAJA:
     * <ul>
     *   <li>Debito: Caja/Banco por disposalAmount</li>
     *   <li>Si ganancia: Credito activo por bookValue + Credito ganancia por gainLoss</li>
     *   <li>Si perdida: Credito activo por bookValue + Debito perdida por |gainLoss|</li>
     * </ul>
     *
     * @param disposal  disposicion registrada
     * @param asset     activo original
     * @param bookValue valor en libros
     * @param gainLoss  ganancia o perdida
     * @return id del asiento creado, o null si no aplica
     */
    private Long createJournalEntry(AssetDisposal disposal, Assets asset,
                                     BigDecimal bookValue, BigDecimal gainLoss) {
        if (disposal.getDisposalType() != DisposalType.BAJA) {
            return null;
        }

        BigDecimal disposalAmount = disposal.getDisposalAmount();
        if (disposalAmount == null || disposalAmount.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        Long assetAccountId = asset.getAccountingAccount() != null
                ? asset.getAccountingAccount().getId()
                : null;
        if (assetAccountId == null) {
            log.warn("El activo {} no tiene cuenta contable asociada, no se genera asiento.", asset.getId());
            return null;
        }

        // ACT-03: Usar cuentas parametrizadas via AccountMappingService en vez de placeholder.
        //   Caja default (PUC 1105) para ingresos por enajenacion
        //   Dif. cambio ingreso (PUC 4215) proxy de "otros ingresos" para ganancia
        //   Dif. cambio gasto (PUC 5305)  proxy de "otros gastos"   para perdida
        Long cajaAccountId = accountMappingService.resolveOrThrow(AccountingConcept.CAJA_DEFAULT);
        Long gainAccountId = accountMappingService.resolveOrThrow(AccountingConcept.DIF_CAMBIO_INGRESO);
        Long lossAccountId = accountMappingService.resolveOrThrow(AccountingConcept.DIF_CAMBIO_GASTO);

        List<CreateJournalEntryLineRequest> lines = new ArrayList<>();

        // Linea 1: Debito Caja por monto de enajenacion (PUC 1105)
        lines.add(CreateJournalEntryLineRequest.builder()
                .accountingAccountId(cajaAccountId)
                .debitAmount(disposalAmount)
                .creditAmount(BigDecimal.ZERO)
                .description("Ingreso por baja de activo: " + asset.getAssetCode())
                .build());

        // Linea 2: Credito Activo por valor en libros (cuenta del activo)
        lines.add(CreateJournalEntryLineRequest.builder()
                .accountingAccountId(assetAccountId)
                .debitAmount(BigDecimal.ZERO)
                .creditAmount(bookValue)
                .description("Baja de activo: " + asset.getAssetCode())
                .build());

        // Linea 3: Ganancia o perdida contra cuenta de otros ingresos/gastos parametrizada
        if (gainLoss.compareTo(BigDecimal.ZERO) > 0) {
            // Ganancia: credito a otros ingresos (PUC 4215 proxy)
            lines.add(CreateJournalEntryLineRequest.builder()
                    .accountingAccountId(gainAccountId)
                    .debitAmount(BigDecimal.ZERO)
                    .creditAmount(gainLoss)
                    .description("Ganancia en baja de activo: " + asset.getAssetCode())
                    .build());
        } else if (gainLoss.compareTo(BigDecimal.ZERO) < 0) {
            // Perdida: debito a otros gastos (PUC 5305 proxy)
            lines.add(CreateJournalEntryLineRequest.builder()
                    .accountingAccountId(lossAccountId)
                    .debitAmount(gainLoss.abs())
                    .creditAmount(BigDecimal.ZERO)
                    .description("Perdida en baja de activo: " + asset.getAssetCode())
                    .build());
        }

        CreateJournalEntryRequest jeRequest = CreateJournalEntryRequest.builder()
                .entryDate(disposal.getDisposalDate())
                .description("Baja de activo " + asset.getAssetCode() + " - " + disposal.getReason())
                .sourceModule(JournalSourceModule.ACT)
                .sourceId(disposal.getId())
                .lines(lines)
                .build();

        JournalEntryDTO created = journalEntryService.createEntry(jeRequest, "sistema");
        return created.getId();
    }

    // ───────────────────────────────────────────────────────────────
    // Mapeo entidad -> DTO
    // ───────────────────────────────────────────────────────────────

    /**
     * Convierte una entidad AssetDisposal a su DTO de lectura.
     *
     * @param disposal entidad a convertir
     * @return DTO con los datos de la disposicion
     */
    private AssetDisposalDTO toDTO(AssetDisposal disposal) {
        return AssetDisposalDTO.builder()
                .id(disposal.getId())
                .assetId(disposal.getAsset().getId())
                .assetCode(disposal.getAsset().getAssetCode())
                .assetName(disposal.getAsset().getAssetName())
                .disposalType(disposal.getDisposalType())
                .disposalDate(disposal.getDisposalDate())
                .disposalAmount(disposal.getDisposalAmount())
                .bookValueAtDisposal(disposal.getBookValueAtDisposal())
                .gainLoss(disposal.getGainLoss())
                .reason(disposal.getReason())
                .destinationInfo(disposal.getDestinationInfo())
                .journalEntryId(disposal.getJournalEntryId())
                .createdAt(disposal.getCreatedAt())
                .build();
    }
}
