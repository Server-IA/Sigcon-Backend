package com.sigcon.backend.checkbooks.domain.repository;

import com.sigcon.backend.checkbooks.domain.model.Checkbook;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CheckbookRepository extends JpaRepository<Checkbook, Long> {

    boolean existsByBankAccountIdAndCheckbookNumber(Long bankAccountId, String checkbookNumber);

    List<Checkbook> findByBankAccountId(Long bankAccountId);

    long countByBankAccountId(Long bankAccountId);
}