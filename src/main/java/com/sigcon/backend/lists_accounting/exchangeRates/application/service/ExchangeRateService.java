package com.sigcon.backend.lists_accounting.exchangeRates.application.service;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;

import com.sigcon.backend.lists_accounting.exchangeRates.application.dto.CreateExchangeRateRequest;
import com.sigcon.backend.lists_accounting.exchangeRates.application.dto.ExchangeRateDTO;
import com.sigcon.backend.lists_accounting.exchangeRates.application.dto.ExchangeRateFilterRequest;
import com.sigcon.backend.lists_accounting.exchangeRates.application.dto.UpdateExchangeRateRequest;
import com.sigcon.backend.lists_accounting.exchangeRates.domain.model.ExchangeRate;
import com.sigcon.backend.lists_accounting.exchangeRates.domain.model.Enums.StatusCurrencyExchange;
import com.sigcon.backend.lists_accounting.exchangeRates.domain.repository.ExchangeRateRepository;
import com.sigcon.backend.lists_accounting.types_of_currency.application.CurrencyTypeResponseDTO;
import com.sigcon.backend.lists_accounting.types_of_currency.domain.model.CurrencyType;
import com.sigcon.backend.lists_accounting.types_of_currency.domain.repository.CurrencyTypeRepository;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Servicio de gestion de tasas de cambio (modulo CFG - Listas Contables).
 * <p>
 * Implementa las historias CFG-RF-31 a CFG-RF-34: crear, consultar, editar y eliminar
 * tasas de cambio entre monedas. Cada tasa tiene un rango de vigencia (startDate - endDate)
 * y se valida que no haya solapamiento de fechas para la misma combinacion de
 * moneda origen, moneda destino y tipo de cambio.
 * </p>
 *
 * @see com.sigcon.backend.lists_accounting.exchangeRates.domain.model.ExchangeRate
 */
@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    private final ExchangeRateRepository repository;
    private final CurrencyTypeRepository currencyRepository;
    private final DataTableSpecificationBuilder<ExchangeRate> exchangeRateSpecificationBuilder = new DataTableSpecificationBuilder<>();

    /**
     * Crea una nueva tasa de cambio (CFG-RF-31).
     * <p>
     * Valida que las fechas sean coherentes (inicio <= fin), que no exista solapamiento
     * con otra tasa activa para la misma combinacion moneda/tipo, y que ambas monedas
     * existan y esten activas.
     * </p>
     *
     * @param request       datos de la tasa (monedas, tipo, valor, rango de fechas)
     * @param bindingResult resultado de validacion de campos
     * @return ResponseEntity con confirmacion o error de validacion
     * @throws RuntimeException si alguna moneda no existe
     */
    public ResponseEntity<?> create(CreateExchangeRateRequest request, BindingResult bindingResult) {
        // try{
            if (bindingResult.hasErrors()) {
                return ResponseEntity.badRequest().body(bindingResult.getAllErrors());
            }

            if (request.getStartDate().isAfter(request.getEndDate())) {
                return ResponseEntity.badRequest().body("La fecha inicio no puede ser mayor a la fecha fin");
            }

            // Validar solapamiento: no puede existir otra tasa activa para la misma moneda/tipo en el rango
            boolean overlap = repository.existsOverlap(
                    request.getCurrencyId(),
                    request.getCurrencyIso(),
                    request.getExchangeType(),
                    request.getStartDate(),
                    request.getEndDate()
            );

            if (overlap) {
                return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("Ya existe una tasa activa para esta moneda y tipo de cambio en el rango indicado"))
                );
            }

            CurrencyType currencyExchange = currencyRepository.findByIdAndDeletedAtIsNull(request.getCurrencyId())
                    .orElseThrow(() -> new RuntimeException("La moneda cambiada no existe"));

            CurrencyType currencyExchanged = currencyRepository.findByIdAndDeletedAtIsNull(request.getCurrencyIso())
                    .orElseThrow(() -> new RuntimeException("La moneda a cambiar no existe"));

            ExchangeRate rate = ExchangeRate.builder()
                    .currencyExchange(currencyExchange)
                    .currencyExchanged(currencyExchanged)
                    .exchangeType(request.getExchangeType())
                    .value(request.getValue())
                    .startDate(request.getStartDate())
                    .endDate(request.getEndDate())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            repository.save(rate);
            return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Tasa de cambio creado con exito"), Optional.empty())
            );
        // }catch(Exception e){
        //     return ResponseEntity.badRequest().body(
        //         ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage()))
        //     );
        // }
    }

    /**
     * Consulta paginada de tasas de cambio con filtros dinamicos (CFG-RF-32).
     * Excluye automaticamente registros con soft delete.
     *
     * @param request parametros de paginacion, busqueda y ordenamiento del DataTable
     * @return ResponseEntity con DataTableResponse paginado de ExchangeRateDTO
     */
    public ResponseEntity<?> findAll(DataTableRequest request) {

        int start = Math.max(0, request.getStart());
        int length = request.getLength();

        int safeLength = length <= 0 ? 10 : length;
        int page = start / safeLength;

        Pageable pageable = length == -1
            ? Pageable.unpaged()
            : PageRequest.of(page, safeLength);

        Specification<ExchangeRate> spec = exchangeRateSpecificationBuilder.build(request)
            .and((root, query, cb) -> cb.isNull(root.get("deletedAt")));

        Page<ExchangeRate> exchangeRates = repository.findAll(spec, pageable);

        return ResponseEntity.ok(
            DataTableResponse.from(
                exchangeRates.map(exchangeRate -> ExchangeRateDTO.builder()
                    .id(exchangeRate.getId())
                    .currencyExchange(
                        exchangeRate.getCurrencyExchange() != null ?
                            getCurrencyTypeResponseDTO(exchangeRate.getCurrencyExchange())
                            : null
                    ).currencyExchanged(
                        exchangeRate.getCurrencyExchanged() != null ?
                            getCurrencyTypeResponseDTO(exchangeRate.getCurrencyExchanged())
                                : null
                    )
                    .exchangeType(exchangeRate.getExchangeType())
                    .value(exchangeRate.getValue())
                    .startDate(exchangeRate.getStartDate())
                    .endDate(exchangeRate.getEndDate())
                    .status(exchangeRate.getStatus())
                    .build())
                , request.getDraw())
        );
    }

    /**
     * Actualiza una tasa de cambio existente (CFG-RF-33).
     * <p>
     * Valida que el valor sea positivo, las fechas coherentes, y que no haya
     * solapamiento con otra tasa (excluyendo el registro actual).
     * </p>
     *
     * @param id            identificador de la tasa a actualizar
     * @param request       datos actualizados (monedas, tipo, valor, fechas, estado)
     * @param bindingResult resultado de validacion de campos
     * @return ResponseEntity con confirmacion o error de validacion
     * @throws RuntimeException si la tasa o las monedas no existen
     */
    public ResponseEntity<?> update(Long id, UpdateExchangeRateRequest request, BindingResult bindingResult) {

        // try{

            if (bindingResult.hasErrors()) {
                return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondJson(bindingResult)
                );
            }

            ExchangeRate rate = repository.findById(id)
                    .orElseThrow(() -> new RuntimeException("La tasa no existe"));

            if (request.getValue() <= 0) {
                return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("La tasa debe ser mayor a 0"))
                );
            }

            if (request.getStartDate().isAfter(request.getEndDate())) {
                return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("Fechas inválidas"))
                );
            }

            // Validar solapamiento excluyendo el registro actual (para no detectarse a si mismo)
            boolean overlap = repository.existsOverlapForUpdate(
                    request.getCurrencyId(),
                    request.getCurrencyIso(),
                    request.getExchangeType(),
                    request.getStartDate(),
                    request.getEndDate(),
                    id
            );

            if (overlap) {
                return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("Ya existe otra tasa activa para este rango de fechas y tipo de cambio"))
                );
            }
    
            CurrencyType currencyExchange = currencyRepository.findByIdAndDeletedAtIsNull(request.getCurrencyId())
                    .orElseThrow(() -> new RuntimeException("La moneda cambiada no existe"));
            CurrencyType currencyExchanged = currencyRepository.findByIdAndDeletedAtIsNull(request.getCurrencyIso())
                    .orElseThrow(() -> new RuntimeException("La moneda a cambiar no existe"));
    
            rate.setCurrencyExchange(currencyExchange);
            rate.setCurrencyExchanged(currencyExchanged);
            rate.setExchangeType(request.getExchangeType());
            rate.setValue(request.getValue());
            rate.setStartDate(request.getStartDate());
            rate.setEndDate(request.getEndDate());
            rate.setStatus(request.getStatus());
            rate.setUpdatedAt(LocalDateTime.now());
    
            repository.save(rate);
    
            return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Tasa de cambio actualizada con exito"), Optional.empty())
            );
            
        // }catch(Exception e){
        //     return ResponseEntity.badRequest().body(
        //         ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage()))
        //     );
        // }
    }

    /**
     * Elimina (soft delete) una tasa de cambio (CFG-RF-34).
     * Marca el registro con deletedAt en lugar de eliminarlo fisicamente.
     *
     * @param id identificador de la tasa a eliminar
     * @return ResponseEntity con confirmacion de eliminacion
     * @throws RuntimeException si la tasa no existe
     */
    public ResponseEntity<?> delete(Long id) {

        // try{

            ExchangeRate rate = repository.findById(id)
                    .orElseThrow(() -> new RuntimeException("La tasa no existe"));
    
            rate.setDeletedAt(LocalDateTime.now());
            rate.setUpdatedAt(LocalDateTime.now());
    
            repository.save(rate);
    
            return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Tasa de cambio eliminada con exito"), Optional.empty())
            );

        // }catch(Exception e){
        //     return ResponseEntity.badRequest().body(
        //         ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage()))
        //     );
        // }
    }

    /**
     * Convierte una entidad CurrencyType a su DTO de respuesta para la tasa de cambio.
     * Retorna null si la moneda no se encuentra en BD.
     */
    private CurrencyTypeResponseDTO getCurrencyTypeResponseDTO(CurrencyType currencyType) {
        CurrencyType currencyTypeResponseDTO = currencyRepository.findById(currencyType.getId()).orElse(null);
        if (currencyTypeResponseDTO != null) {
            return CurrencyTypeResponseDTO.builder()
                    .id(currencyTypeResponseDTO.getId())
                    .isoCode(currencyTypeResponseDTO.getIsoCode())
                    .name(currencyTypeResponseDTO.getName())
                    .build();
        }
        return null;
    }
}