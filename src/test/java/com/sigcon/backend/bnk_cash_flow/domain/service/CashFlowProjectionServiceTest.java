package com.sigcon.backend.bnk_cash_flow.domain.service;

import com.sigcon.backend.bnk_cash_flow.application.CreateCashFlowProjectionDTO;
import com.sigcon.backend.bnk_cash_flow.application.UpdateCashFlowProjectionDTO;
import com.sigcon.backend.bnk_cash_flow.application.ViewCashFlowProjectionDTO;
import com.sigcon.backend.bnk_cash_flow.domain.model.CashFlowProjection;
import com.sigcon.backend.bnk_cash_flow.domain.model.enums.ProjectionPeriodicity;
import com.sigcon.backend.bnk_cash_flow.domain.model.enums.ProjectionStatus;
import com.sigcon.backend.bnk_cash_flow.domain.model.enums.ProjectionType;
import com.sigcon.backend.bnk_cash_flow.domain.repository.CashFlowProjectionRepository;
import com.sigcon.backend.utils.SuccessRespondJson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CashFlowProjectionServiceTest {

    @Mock
    private CashFlowProjectionRepository projectionRepository;

    @Mock
    private BindingResult bindingResult;

    @InjectMocks
    private CashFlowProjectionService service;

    private CashFlowProjection sampleProjection;

    @BeforeEach
    void setUp() {
        sampleProjection = CashFlowProjection.builder()
                .id(1L)
                .name("Proyección 2026")
                .description("Test")
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .periodicity(ProjectionPeriodicity.MENSUAL)
                .projectionType(ProjectionType.NETA)
                .initialBalance(new BigDecimal("1000"))
                .netFlow(new BigDecimal("500"))
                .finalBalance(new BigDecimal("1500"))
                .currency("COP")
                .status(ProjectionStatus.BORRADOR)
                .build();
    }

    @Test
    void testCreateSuccess() {
        CreateCashFlowProjectionDTO dto = new CreateCashFlowProjectionDTO();
        dto.setName("New Projection");
        dto.setStartDate(LocalDate.of(2026, 1, 1));
        dto.setEndDate(LocalDate.of(2026, 12, 31));
        dto.setInitialBalance(new BigDecimal("1000"));
        dto.setNetFlow(new BigDecimal("500"));
        dto.setCurrency("COP");
        dto.setDescription("Desc");
        dto.setPeriodicity(ProjectionPeriodicity.MENSUAL);
        dto.setProjectionType(ProjectionType.NETA);

        when(bindingResult.hasErrors()).thenReturn(false);
        when(projectionRepository.existsByNameAndDeletedAtIsNull("New Projection")).thenReturn(false);
        when(projectionRepository.save(any(CashFlowProjection.class))).thenAnswer(i -> {
            CashFlowProjection p = i.getArgument(0);
            p.setId(2L);
            return p;
        });

        ResponseEntity<?> response = service.create(dto, bindingResult);

        assertEquals(200, response.getStatusCode().value());
        verify(projectionRepository).save(any(CashFlowProjection.class));
    }

    @Test
    void testUpdateSuccess() {
        UpdateCashFlowProjectionDTO dto = new UpdateCashFlowProjectionDTO();
        dto.setName("Updated Name");
        dto.setInitialBalance(new BigDecimal("2000"));
        dto.setNetFlow(new BigDecimal("500"));

        when(bindingResult.hasErrors()).thenReturn(false);
        when(projectionRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(sampleProjection));
        when(projectionRepository.existsByNameAndIdNotAndDeletedAtIsNull("Updated Name", 1L)).thenReturn(false);

        ResponseEntity<?> response = service.update(1L, dto, bindingResult);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("Updated Name", sampleProjection.getName());
        assertEquals(new BigDecimal("2500"), sampleProjection.getFinalBalance());
        verify(projectionRepository).save(sampleProjection);
    }

    @Test
    void testDeleteLogicallySuccess() {
        when(projectionRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(sampleProjection));

        ResponseEntity<?> response = service.delete(1L);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(ProjectionStatus.INACTIVA, sampleProjection.getStatus());
        assertNotNull(sampleProjection.getDeletedAt());
        verify(projectionRepository).save(sampleProjection);
    }

    @Test
    void testInactivateSuccess() {
        when(projectionRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(sampleProjection));

        ResponseEntity<?> response = service.inactivate(1L);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(ProjectionStatus.INACTIVA, sampleProjection.getStatus());
        assertNull(sampleProjection.getDeletedAt()); // Not logically deleted
        verify(projectionRepository).save(sampleProjection);
    }

    @Test
    void testGetDetailSuccess() {
        when(projectionRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(sampleProjection));

        ResponseEntity<?> response = service.getDetail(1L);

        assertEquals(200, response.getStatusCode().value());
    }
}
