package com.sigcon.backend.general.accounting.journal.application;

import java.time.LocalDate;
import java.util.List;

import com.sigcon.backend.general.accounting.journal.domain.model.enums.JournalSourceModule;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para crear un nuevo asiento contable.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateJournalEntryRequest {

    @NotNull(message = "La fecha del asiento es obligatoria.")
    private LocalDate entryDate;

    private String description;

    @NotNull(message = "El modulo origen es obligatorio.")
    private JournalSourceModule sourceModule;

    private Long sourceId;

    @NotEmpty(message = "El asiento debe tener al menos una linea.")
    @Valid
    private List<CreateJournalEntryLineRequest> lines;
}
