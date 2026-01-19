package com.sigcon.backend.accounting_lists.application;

import com.sigcon.backend.accounting_lists.domain.model.enums.AccountClass;
import com.sigcon.backend.accounting_lists.domain.model.enums.AccountLevel;
import com.sigcon.backend.accounting_lists.domain.model.enums.AccountNature;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    @NotBlank(message = "El código oficial de la cuenta es obligatorio")
    @Size(min = 1, max = 10, message = "El código oficial debe tener entre 1 y 10 caracteres")
    private String code;

    @NotBlank(message = "El nombre de la cuenta es obligatorio")
    @Size(min = 1, max = 100, message = "El nombre de la cuenta debe tener máximo 100 caracteres")
    private String name;

    @NotNull(message = "La clase de la cuenta es obligatoria")
    private AccountClass accountClass;

    @NotNull(message = "El nivel jerárquico de la cuenta es obligatorio")
    private AccountLevel level;

    @NotNull(message = "La naturaleza de la cuenta es obligatoria")
    private AccountNature nature;

}
