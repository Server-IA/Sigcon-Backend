package com.sigcon.backend.accounting_lists.application;

import com.sigcon.backend.accounting_lists.domain.model.enums.AccountClass;
import com.sigcon.backend.accounting_lists.domain.model.enums.AccountLevel;
import com.sigcon.backend.accounting_lists.domain.model.enums.AccountNature;
import com.sigcon.backend.accounting_lists.domain.model.enums.AccountStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChartOfAccountDTO {

    @Pattern(regexp = "^[\\w\\-\\s]{1,100}$", message = "Por favor siga el formato de los filtros")
    private String code;

    @Pattern(regexp = "^[\\w\\-\\s]{1,100}$", message = "Por favor siga el formato de los filtros")
    private String name;

    private AccountClass accountClass;

    private AccountLevel level;

    private AccountNature nature;

    private AccountStatus status;

}
