package com.sigcon.backend.banks.banks.domain.service;

import com.sigcon.backend.banks.banks.application.BankBranchDTO;
import com.sigcon.backend.banks.banks.application.BankDTO;
import com.sigcon.backend.banks.banks.domain.model.Bank;
import com.sigcon.backend.banks.banks.domain.model.BankBranch;
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

        private final DataTableSpecificationBuilder<Bank> dataTableSpecificationBuilder = new DataTableSpecificationBuilder<>();

        /**
         * Crear banco
         */
        public ResponseEntity<?> create(BankDTO request, BindingResult bindingResult) {

                if (bindingResult.hasErrors()) {
                        return ResponseEntity.badRequest()
                                        .body(ErrorRespondJson.getErrorRespondJson(bindingResult));
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

                if (bankPage.isEmpty()) {
                        throw new IllegalArgumentException("No se encontraron bancos.");
                }

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
                if (request.getStatus() != null)
                        bank.setStatus(request.getStatus());
                if (request.getFormatExtract() != null)
                        bank.setFormatExtract(request.getFormatExtract());

                bankRepository.save(bank);

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

                bank.setDeletedAt(LocalDateTime.now());

                bankRepository.save(bank);

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

                return BankDTO.builder()
                                .id(bank.getId())
                                .code(bank.getCode())
                                .name(bank.getName())
                                .nameShort(bank.getNameShort())
                                .typeBank(bank.getTypeBank())
                                .nit(bank.getNit())
                                .swift(bank.getSwift())
                                .codeAch(bank.getCodeAch())
                                .phone(bank.getPhone())
                                .status(bank.getStatus())
                                .country(country != null ? toCountryDto(country) : null)
                                .countryId(country != null ? country.getId() : null)
                                .branches(branches != null // ← esto faltaba
                                                ? branches.stream().map(this::toBranchDto).toList()
                                                : List.of())
                                .createdAt(bank.getCreatedAt())
                                .updatedAt(bank.getUpdatedAt())
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