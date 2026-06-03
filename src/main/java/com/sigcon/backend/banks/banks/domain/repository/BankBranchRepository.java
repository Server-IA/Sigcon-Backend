package com.sigcon.backend.banks.banks.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.sigcon.backend.banks.banks.domain.model.BankBranch;

import java.util.List;
import java.util.Optional;

public interface BankBranchRepository extends JpaRepository<BankBranch, Long>, JpaSpecificationExecutor<BankBranch> {

    boolean existsByAddressAndDeletedAtIsNull(String address);

    boolean existsByAddressAndIdNotAndDeletedAtIsNull(String address, Long id);

    // BNK-RF-26 (QA Bloque BNK 2026-06-03): no permitir dos sucursales con la misma
    // dirección y ciudad (municipio) para el mismo banco.
    boolean existsByBank_IdAndMunicipality_IdAndAddressIgnoreCaseAndDeletedAtIsNull(
            Long bankId, Long municipalityId, String address);

    boolean existsByBank_IdAndMunicipality_IdAndAddressIgnoreCaseAndIdNotAndDeletedAtIsNull(
            Long bankId, Long municipalityId, String address, Long id);

    Optional<BankBranch> findByIdAndDeletedAtIsNull(Long id);

    List<BankBranch> findByBankIdAndDeletedAtIsNull(Long bankId);

    Optional<BankBranch> findByBankIdAndMainBranchTrueAndDeletedAtIsNull(Long bankId);

    List<BankBranch> findByMunicipalityNameContainingIgnoreCaseAndDeletedAtIsNull(String municipalityName);

    List<BankBranch> findByBankIdAndMunicipalityNameContainingIgnoreCaseAndDeletedAtIsNull(Long bankId,
            String municipalityName);

    boolean existsByBankIdAndMainBranchTrueAndIdNot(Long bankId, Long id);
}