package com.sigcon.backend.accounting_lists.domain.service;

import com.sigcon.backend.accounting_lists.application.ChartOfAccountDTO;
import com.sigcon.backend.accounting_lists.domain.model.ChartOfAccount;
import com.sigcon.backend.accounting_lists.domain.model.enums.AccountClass;
import com.sigcon.backend.accounting_lists.domain.model.enums.AccountLevel;
import com.sigcon.backend.accounting_lists.domain.model.enums.AccountNature;
import com.sigcon.backend.accounting_lists.domain.model.enums.AccountStatus;
import com.sigcon.backend.accounting_lists.domain.repository.ChartOfAccountRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChartOfAccountService {

    private final ChartOfAccountRepository chartOfAccountRepository;


    public void validateAccountNature(AccountClass accountClass, AccountNature accountNature) {

        if (accountClass == null || accountNature == null) {
            throw new IllegalArgumentException("Deben proporcionarse la clase y naturaleza de la cuenta");
        }

        switch (accountClass) {
            case ASSET:
            case EXPENSE:
            case COST_OF_SALES:
            case PRODUCTION_COST:
            case MEMORANDUM_DEBIT:
                if (accountNature != AccountNature.DEBIT) {
                    throw new IllegalStateException(
                            "Naturaleza de la cuenta no es válida " + accountClass
                    );
                }
                break;

            case LIABILITY:
            case EQUITY:
            case REVENUE:
            case MEMORANDUM_CREDIT:
                if (accountNature != AccountNature.CREDIT) {
                    throw new IllegalStateException(
                            "Naturaleza de la cuenta no es válida " + accountClass
                    );
                }
                break;
        }
    }

    public void validateAccountLevel(AccountLevel accountLevel) {
        if (accountLevel == null) {
            throw new IllegalArgumentException("Debe proporcionarse el nivel de la cuenta");
        }


        if (accountLevel == AccountLevel.SUBGROUP) {
            // Aquí luego validaremos existencia de grupo padre
        }
    }


    @Transactional
    public void createChartOfAccount(ChartOfAccountDTO request) {


        if (chartOfAccountRepository.existsByCode(request.getCode())) {
            throw new IllegalArgumentException("Código oficial ya registrado");
        }

        if (chartOfAccountRepository.existsByNameIgnoreCase(request.getName())) {
            throw new IllegalArgumentException("Nombre ya registrado");
        }

        validateAccountingLogic(request);


        ChartOfAccount account = ChartOfAccount.builder()
                .code(request.getCode())
                .name(request.getName())
                .accountClass(request.getAccountClass())
                .accountLevel(request.getLevel())
                .accountNature(request.getNature())
                .status(AccountStatus.ACTIVE)
                .build();

        chartOfAccountRepository.save(account);
    }

    private void validateAccountingLogic(ChartOfAccountDTO request) {


        if (request.getLevel() == AccountLevel.SUBGROUP
                && request.getAccountClass() == null) {
            throw new IllegalArgumentException("Jerarquía no válida");
        }

        if (request.getNature() == null) {
            throw new IllegalArgumentException("Naturaleza de la cuenta no válida");
        }
    }


    public Page<ChartOfAccount> searchChartOfAccounts(ChartOfAccountDTO request, Pageable pageable) {

        if (chartOfAccountRepository.count() == 0) {
            throw new IllegalStateException("No existen cuentas registradas en el catálogo PUC");
        }

        Page<ChartOfAccount> result =
                chartOfAccountRepository.searchChartOfAccounts(
                        request.getCode(),
                        request.getName(),
                        request.getAccountClass(),
                        request.getLevel(),
                        request.getNature(),
                        request.getStatus(),
                        pageable
                );

        if (result.isEmpty()) {
            throw new IllegalArgumentException("No existen cuentas con estos criterios");
        }

        return result;
    }


}