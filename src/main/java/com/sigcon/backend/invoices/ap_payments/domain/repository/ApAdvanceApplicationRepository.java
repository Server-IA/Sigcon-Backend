package com.sigcon.backend.invoices.ap_payments.domain.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.sigcon.backend.invoices.ap_payments.domain.model.ApAdvanceApplication;

/**
 * AP-RF-05 E6/E7 (Bloque DV): repositorio de aplicaciones de anticipo.
 */
@Repository
public interface ApAdvanceApplicationRepository extends JpaRepository<ApAdvanceApplication, Long> {

    List<ApAdvanceApplication> findByAdvanceIdAndDeletedAtIsNullOrderByCreatedAtAsc(Long advanceId);

    List<ApAdvanceApplication> findByAdvanceIdAndStatusAndDeletedAtIsNull(Long advanceId, String status);

    Optional<ApAdvanceApplication> findByIdAndDeletedAtIsNull(Long id);
}
