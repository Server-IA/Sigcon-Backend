package com.sigcon.backend.banks.banks.domain.service;

import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;
import com.sigcon.backend.banks.bankaccounts.domain.repository.BankAccountRepository;
import com.sigcon.backend.banks.banks.application.BankBranchDTO;
import com.sigcon.backend.banks.banks.application.BankDTO;
import com.sigcon.backend.banks.banks.domain.model.Bank;
import com.sigcon.backend.banks.banks.domain.model.BankBranch;
import com.sigcon.backend.banks.banks.domain.model.enums.BankStatus;
import com.sigcon.backend.banks.banks.domain.repository.BankRepository;
import com.sigcon.backend.parametrization.resources.application.CountryDTO;
import com.sigcon.backend.parametrization.resources.application.MunicipalityDTO;
import com.sigcon.backend.parametrization.resources.domain.model.Country;
import com.sigcon.backend.parametrization.resources.domain.repository.CountryRepository;

import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import org.springframework.validation.BindingResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BankService {

        private final BankRepository bankRepository;
        private final CountryRepository countryRepository;
        private final BankAccountRepository bankAccountRepository;
        private final AuditPublisher auditPublisher;

        private final DataTableSpecificationBuilder<Bank> dataTableSpecificationBuilder = new DataTableSpecificationBuilder<>();

        /**
         * Crear banco
         */
        public ResponseEntity<?> create(BankDTO request, BindingResult bindingResult) {

                if (bindingResult.hasErrors()) {
                        return ResponseEntity.badRequest()
                                        .body(ErrorRespondJson.getErrorRespondJson(bindingResult));
                }

                // HU-005 E3 (Bloque AO): mensaje literal del Excel para codigo duplicado.
                if (bankRepository.existsByCodeAndDeletedAtIsNull(request.getCode().trim())) {
                        return ResponseEntity.badRequest()
                                        .body(ErrorRespondJson.getErrorRespondMessage(
                                                        Optional.of("Ya existe un banco registrado con ese código")));
                }

                // HU-005 E4 (Bloque AO): mensaje literal del Excel para NIT duplicado.
                if (bankRepository.existsByNitAndDeletedAtIsNull(request.getNit().trim())) {
                        return ResponseEntity.badRequest()
                                        .body(ErrorRespondJson.getErrorRespondMessage(
                                                        Optional.of("Ya existe un banco registrado con ese NIT")));
                }

                // QA Bloque AU (2026-05-06) — Bug 1: unicidad explicita en nombre,
                // nombre corto, SWIFT y codigo ACH. Antes el UK de BD producia el
                // mensaje ambiguo "la empresa tiene registros asociados" porque los
                // indices se llaman uk_banks_company_* y el handler matcheaba "compan".
                if (request.getName() != null
                                && bankRepository.existsByNameAndDeletedAtIsNull(request.getName().trim())) {
                        return ResponseEntity.badRequest()
                                        .body(ErrorRespondJson.getErrorRespondMessage(
                                                        Optional.of("Ya existe un banco registrado con ese nombre")));
                }
                if (request.getNameShort() != null
                                && bankRepository.existsByNameShortAndDeletedAtIsNull(request.getNameShort().trim())) {
                        return ResponseEntity.badRequest()
                                        .body(ErrorRespondJson.getErrorRespondMessage(
                                                        Optional.of("Ya existe un banco registrado con ese nombre corto")));
                }
                if (request.getSwift() != null
                                && bankRepository.existsBySwiftAndDeletedAtIsNull(request.getSwift().trim())) {
                        return ResponseEntity.badRequest()
                                        .body(ErrorRespondJson.getErrorRespondMessage(
                                                        Optional.of("Ya existe un banco registrado con ese código SWIFT")));
                }
                if (request.getCodeAch() != null && !request.getCodeAch().isBlank()
                                && bankRepository.existsByCodeAchAndDeletedAtIsNull(request.getCodeAch().trim())) {
                        return ResponseEntity.badRequest()
                                        .body(ErrorRespondJson.getErrorRespondMessage(
                                                        Optional.of("Ya existe un banco registrado con ese código ACH")));
                }

                Country country = resolveCountry(request.getCountryId());

                Bank bank = Bank.builder()
                                .code(request.getCode().trim())
                                .name(request.getName().trim())
                                .nameShort(request.getNameShort().trim())
                                .typeBank(request.getTypeBank())
                                .nit(request.getNit().trim())
                                .swift(request.getSwift().trim())
                                .codeAch(request.getCodeAch())
                                .urlWebservice(request.getUrlWebservice())
                                .conciliationDays(request.getConciliationDays())
                                .phone(request.getPhone())
                                .formatExtract(request.getFormatExtract())
                                .country(country)
                                .build();

                bankRepository.save(bank);

                auditPublisher.publishCreate(AuditModule.BNK, "Bank", bank.getId(),
                                "Banco registrado: " + bank.getName() + " (NIT " + bank.getNit() + ")");

                return ResponseEntity.ok(
                                SuccessRespondJson.getSuccessRespondMessage(
                                                Optional.of("Banco registrado correctamente."),
                                                Optional.of(toDto(bank))));
        }

        /**
         * Consulta paginada (DataTable)
         */
        public ResponseEntity<?> findAllPaged(DataTableRequest request) {

                int start = Math.max(0, request.getStart());
                int length = request.getLength();
                int safeLength = length <= 0 ? 20 : Math.min(length, 100);
                int page = start / safeLength;

                Pageable pageable = length == -1
                                ? Pageable.unpaged()
                                : PageRequest.of(page, safeLength);

                Specification<Bank> specification = dataTableSpecificationBuilder.build(request);
                Page<Bank> bankPage = bankRepository.findAll(specification, pageable);

                // Un listado paginado vacio NO es un error: el frontend renderiza
                // tabla vacia con DataTableResponse (totalElements=0).
                return ResponseEntity.ok(
                                DataTableResponse.from(bankPage.map(this::toDto), request.getDraw()));
        }

        /**
         * Detalle
         */
        public ResponseEntity<?> getDetail(Long id) {

                Bank bank = getBankOrThrow(id);

                return ResponseEntity.ok(
                                SuccessRespondJson.getSuccessRespondMessage(
                                                Optional.of("Detalle del banco obtenido correctamente."),
                                                Optional.of(toDto(bank))));
        }

        /**
         * Actualizar banco
         */
        public ResponseEntity<?> update(Long id, BankDTO request, BindingResult bindingResult) {

                if (bindingResult.hasErrors()) {
                        return ResponseEntity.badRequest()
                                        .body(ErrorRespondJson.getErrorRespondJson(bindingResult));
                }

                Bank bank = getBankOrThrow(id);

                // QA Bloque AU (2026-05-06) — Bug 1: validacion de unicidad
                // excluyendo el id actual, antes de mutar la entidad.
                if (request.getCode() != null
                                && bankRepository.existsByCodeAndIdNotAndDeletedAtIsNull(
                                                request.getCode().trim(), id)) {
                        return ResponseEntity.badRequest()
                                        .body(ErrorRespondJson.getErrorRespondMessage(
                                                        Optional.of("Ya existe un banco registrado con ese código")));
                }
                if (request.getNit() != null
                                && bankRepository.existsByNitAndIdNotAndDeletedAtIsNull(
                                                request.getNit().trim(), id)) {
                        return ResponseEntity.badRequest()
                                        .body(ErrorRespondJson.getErrorRespondMessage(
                                                        Optional.of("Ya existe un banco registrado con ese NIT")));
                }
                if (request.getName() != null
                                && bankRepository.existsByNameAndIdNotAndDeletedAtIsNull(
                                                request.getName().trim(), id)) {
                        return ResponseEntity.badRequest()
                                        .body(ErrorRespondJson.getErrorRespondMessage(
                                                        Optional.of("Ya existe un banco registrado con ese nombre")));
                }
                if (request.getNameShort() != null
                                && bankRepository.existsByNameShortAndIdNotAndDeletedAtIsNull(
                                                request.getNameShort().trim(), id)) {
                        return ResponseEntity.badRequest()
                                        .body(ErrorRespondJson.getErrorRespondMessage(
                                                        Optional.of("Ya existe un banco registrado con ese nombre corto")));
                }
                if (request.getSwift() != null
                                && bankRepository.existsBySwiftAndIdNotAndDeletedAtIsNull(
                                                request.getSwift().trim(), id)) {
                        return ResponseEntity.badRequest()
                                        .body(ErrorRespondJson.getErrorRespondMessage(
                                                        Optional.of("Ya existe un banco registrado con ese código SWIFT")));
                }
                if (request.getCodeAch() != null && !request.getCodeAch().isBlank()
                                && bankRepository.existsByCodeAchAndIdNotAndDeletedAtIsNull(
                                                request.getCodeAch().trim(), id)) {
                        return ResponseEntity.badRequest()
                                        .body(ErrorRespondJson.getErrorRespondMessage(
                                                        Optional.of("Ya existe un banco registrado con ese código ACH")));
                }

                if (request.getCountryId() != null) {
                        bank.setCountry(resolveCountry(request.getCountryId()));
                }

                if (request.getCode() != null)
                        bank.setCode(request.getCode().trim());
                if (request.getName() != null)
                        bank.setName(request.getName().trim());
                if (request.getNameShort() != null)
                        bank.setNameShort(request.getNameShort().trim());
                if (request.getTypeBank() != null)
                        bank.setTypeBank(request.getTypeBank());
                if (request.getNit() != null)
                        bank.setNit(request.getNit().trim());
                if (request.getSwift() != null)
                        bank.setSwift(request.getSwift().trim());
                if (request.getCodeAch() != null)
                        bank.setCodeAch(request.getCodeAch());
                if (request.getUrlWebservice() != null)
                        bank.setUrlWebservice(request.getUrlWebservice());
                if (request.getConciliationDays() != null)
                        bank.setConciliationDays(request.getConciliationDays());
                if (request.getPhone() != null)
                        bank.setPhone(request.getPhone());
                if (request.getStatus() != null) {
                        // Validar que no tenga cuentas activas al cambiar a INACTIVE
                        if (request.getStatus() == BankStatus.INACTIVE
                                        && bankAccountRepository.existsByBankIdAndDeletedAtIsNull(id)) {
                                return ResponseEntity.badRequest()
                                                .body(ErrorRespondJson.getErrorRespondMessage(
                                                                Optional.of("BNK-ERR-003: No se puede desactivar el banco porque tiene cuentas bancarias activas asociadas.")));
                        }
                        bank.setStatus(request.getStatus());
                }
                if (request.getFormatExtract() != null)
                        bank.setFormatExtract(request.getFormatExtract());

                bankRepository.save(bank);

                auditPublisher.publishUpdate(AuditModule.BNK, "Bank", bank.getId(),
                                "Banco actualizado: " + bank.getName());

                return ResponseEntity.ok(
                                SuccessRespondJson.getSuccessRespondMessage(
                                                Optional.of("Banco actualizado correctamente."),
                                                Optional.of(toDto(bank))));
        }

        /**
         * Eliminación lógica
         */
        public ResponseEntity<?> delete(Long id) {

                Bank bank = getBankOrThrow(id);

                // Validar que no tenga cuentas bancarias activas asociadas
                if (bankAccountRepository.existsByBankIdAndDeletedAtIsNull(id)) {
                        return ResponseEntity.badRequest()
                                        .body(ErrorRespondJson.getErrorRespondMessage(
                                                        Optional.of("BNK-ERR-002: No se puede eliminar el banco porque tiene cuentas bancarias activas asociadas.")));
                }

                bank.setDeletedAt(LocalDateTime.now());

                bankRepository.save(bank);

                auditPublisher.publishDelete(AuditModule.BNK, "Bank", bank.getId(),
                                "Banco eliminado: " + bank.getName());

                return ResponseEntity.ok(
                                SuccessRespondJson.getSuccessRespondMessage(
                                                Optional.of("Banco eliminado correctamente."),
                                                Optional.empty()));
        }

        /**
         * ===============================
         * MÉTODOS PRIVADOS
         * ===============================
         */

        private Bank getBankOrThrow(Long id) {
                return bankRepository.findById(id)
                                .orElseThrow(() -> new IllegalArgumentException("El banco no existe o fue eliminado."));
        }

        private Country resolveCountry(Long countryId) {

                if (countryId == null) {
                        throw new IllegalArgumentException("El país es obligatorio.");
                }

                return countryRepository.findById(countryId)
                                .orElseThrow(() -> new IllegalArgumentException("El país no existe."));
        }

        private BankDTO toDto(Bank bank) {

                Country country = bank.getCountry();
                List<BankBranch> branches = bank.getBranches();

                // QA HU-008 E1: flag para que el frontend deshabilite campos
                // criticos (codigo, NIT) cuando el banco ya tiene cuentas asociadas.
                long totalAccounts = bankAccountRepository.countByBank_IdAndDeletedAtIsNull(bank.getId());
                long activeAccounts = bankAccountRepository.countByBank_IdAndStatusAndDeletedAtIsNull(
                        bank.getId(), com.sigcon.backend.banks.bankaccounts.domain.model.enums.BankAccountStatus.ACTIVA);

                return BankDTO.builder()
                                .id(bank.getId())
                                .code(bank.getCode())
                                .name(bank.getName())
                                .nameShort(bank.getNameShort())
                                .typeBank(bank.getTypeBank())
                                .nit(bank.getNit())
                                .swift(bank.getSwift())
                                .codeAch(bank.getCodeAch())
                                .urlWebservice(bank.getUrlWebservice())
                                .formatExtract(bank.getFormatExtract())
                                .phone(bank.getPhone())
                                .status(bank.getStatus())
                                .country(country != null ? toCountryDto(country) : null)
                                .countryId(country != null ? country.getId() : null)
                                .conciliationDays(bank.getConciliationDays())
                                .branches(branches != null // ← esto faltaba
                                                ? branches.stream().map(this::toBranchDto).toList()
                                                : List.of())
                                .createdAt(bank.getCreatedAt())
                                .updatedAt(bank.getUpdatedAt())
                                .hasAssociatedAccounts(totalAccounts > 0)
                                .hasActiveAssociatedAccounts(activeAccounts > 0)
                                .build();
        }

        private CountryDTO toCountryDto(Country country) {

                return CountryDTO.builder()
                                .id(country.getId())
                                .name(country.getName())
                                .code(country.getCode())
                                .createdAt(country.getCreatedAt())
                                .updatedAt(country.getUpdatedAt())
                                .deletedAt(country.getDeletedAt())
                                .build();
        }

        private BankBranchDTO toBranchDto(BankBranch branch) {
                return BankBranchDTO.builder()
                                .id(branch.getId())
                                .address(branch.getAddress())
                                .municipality(branch.getMunicipality() != null // ← reemplaza .city()
                                                ? MunicipalityDTO.builder()
                                                                .id(branch.getMunicipality().getId())
                                                                .name(branch.getMunicipality().getName())
                                                                .build()
                                                : null)
                                .mainBranch(branch.getMainBranch())
                                .bankId(branch.getBank() != null ? branch.getBank().getId() : null)
                                .createdAt(branch.getCreatedAt())
                                .updatedAt(branch.getUpdatedAt())
                                .build();
        }

}