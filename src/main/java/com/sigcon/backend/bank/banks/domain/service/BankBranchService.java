package com.sigcon.backend.bank.banks.domain.service;

import com.sigcon.backend.bank.banks.application.BankBranchDTO;
import com.sigcon.backend.bank.banks.domain.model.Bank;
import com.sigcon.backend.bank.banks.domain.model.BankBranch;
import com.sigcon.backend.bank.banks.domain.repository.BankBranchRepository;
import com.sigcon.backend.bank.banks.domain.repository.BankRepository;

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
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BankBranchService {

    private final BankBranchRepository bankBranchRepository;
    private final BankRepository bankRepository;

    private final DataTableSpecificationBuilder<BankBranch> dataTableSpecificationBuilder =
            new DataTableSpecificationBuilder<>();


    /**
     * Crear sucursal
     */
    public ResponseEntity<?> create(BankBranchDTO request, BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }

        Bank bank = getBankOrThrow(request.getBankId());

        BankBranch branch = BankBranch.builder()
                .address(request.getAddress().trim())
                .city(request.getCity().trim())
                .mainBranch(request.getMainBranch() != null ? request.getMainBranch() : false)
                .bank(bank)
                .build();

        bankBranchRepository.save(branch);

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Sucursal registrada correctamente."),
                        Optional.of(toDto(branch))
                )
        );
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

        Specification<BankBranch> specification = dataTableSpecificationBuilder.build(request);
        Page<BankBranch> branchPage = bankBranchRepository.findAll(specification, pageable);

        if (branchPage.isEmpty()) {
            throw new IllegalArgumentException("No se encontraron sucursales.");
        }

        return ResponseEntity.ok(
                DataTableResponse.from(branchPage.map(this::toDto), request.getDraw())
        );
    }


    /**
     * Detalle
     */
    public ResponseEntity<?> getDetail(Long id) {

        BankBranch branch = getBranchOrThrow(id);

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Detalle de la sucursal obtenido correctamente."),
                        Optional.of(toDto(branch))
                )
        );
    }


    /**
     * Listar sucursales por banco
     */
    public ResponseEntity<?> findByBank(Long bankId) {

        getBankOrThrow(bankId);

        java.util.List<BankBranch> branches =
                bankBranchRepository.findByBankIdAndDeletedAtIsNull(bankId);

        if (branches.isEmpty()) {
            throw new IllegalArgumentException("El banco no tiene sucursales registradas.");
        }

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Sucursales obtenidas correctamente."),
                        Optional.of(branches.stream().map(this::toDto).toList())
                )
        );
    }


    /**
     * Actualizar sucursal
     */
    public ResponseEntity<?> update(Long id, BankBranchDTO request, BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }

        BankBranch branch = getBranchOrThrow(id);

        if (request.getBankId() != null) {
            branch.setBank(getBankOrThrow(request.getBankId()));
        }

        if (request.getAddress() != null) branch.setAddress(request.getAddress().trim());
        if (request.getCity() != null) branch.setCity(request.getCity().trim());
        if (request.getMainBranch() != null) branch.setMainBranch(request.getMainBranch());

        bankBranchRepository.save(branch);

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Sucursal actualizada correctamente."),
                        Optional.of(toDto(branch))
                )
        );
    }


    /**
     * Eliminación lógica
     */
    public ResponseEntity<?> delete(Long id) {

        BankBranch branch = getBranchOrThrow(id);

        branch.setDeletedAt(LocalDateTime.now());

        bankBranchRepository.save(branch);

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Sucursal eliminada correctamente."),
                        Optional.empty()
                )
        );
    }


    /**
     * ===============================
     * MÉTODOS PRIVADOS
     * ===============================
     */

    private BankBranch getBranchOrThrow(Long id) {
        return bankBranchRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("La sucursal no existe o fue eliminada."));
    }

    private Bank getBankOrThrow(Long bankId) {
        if (bankId == null) {
            throw new IllegalArgumentException("El banco es obligatorio.");
        }
        return bankRepository.findById(bankId)
                .orElseThrow(() ->
                        new IllegalArgumentException("El banco no existe o fue eliminado."));
    }

    private BankBranchDTO toDto(BankBranch branch) {
        return BankBranchDTO.builder()
                .id(branch.getId())
                .address(branch.getAddress())
                .city(branch.getCity())
                .mainBranch(branch.getMainBranch())
                .bankId(branch.getBank() != null ? branch.getBank().getId() : null)
                .createdAt(branch.getCreatedAt())
                .updatedAt(branch.getUpdatedAt())
                .build();
    }
}