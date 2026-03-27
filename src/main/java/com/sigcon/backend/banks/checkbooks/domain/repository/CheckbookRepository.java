package com.sigcon.backend.banks.checkbooks.domain.repository;

import com.sigcon.backend.banks.checkbooks.domain.model.Checkbook;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CheckbookRepository extends JpaRepository<Checkbook, Long> {

    boolean existsByBankAccount_IdAndCheckbookNumber(Long bankAccountId, String number);

    List<Checkbook> findByBankAccount_Id(Long bankAccountId);

    long countByBankAccount_Id(Long bankAccountId);
}