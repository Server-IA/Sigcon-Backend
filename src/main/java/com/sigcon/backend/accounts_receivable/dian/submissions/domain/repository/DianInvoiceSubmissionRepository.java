package com.sigcon.backend.accounts_receivable.dian.submissions.domain.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sigcon.backend.accounts_receivable.dian.submissions.domain.model.DianInvoiceSubmission;

/**
 * Repositorio de envios DIAN.
 */
public interface DianInvoiceSubmissionRepository extends JpaRepository<DianInvoiceSubmission, Long> {

    List<DianInvoiceSubmission> findBySalesInvoiceIdAndDeletedAtIsNullOrderByIdDesc(Long salesInvoiceId);

    Optional<DianInvoiceSubmission> findFirstBySalesInvoiceIdAndDeletedAtIsNullOrderByIdDesc(Long salesInvoiceId);
}
