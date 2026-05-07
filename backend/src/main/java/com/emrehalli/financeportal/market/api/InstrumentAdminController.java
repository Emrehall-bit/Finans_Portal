package com.emrehalli.financeportal.market.api;

import com.emrehalli.financeportal.common.i18n.AppMessageSource;
import com.emrehalli.financeportal.common.response.ApiResponse;
import com.emrehalli.financeportal.market.api.dto.admin.InstrumentAdminRequest;
import com.emrehalli.financeportal.market.api.dto.admin.InstrumentAdminResponse;
import com.emrehalli.financeportal.market.api.dto.admin.InstrumentMappingAdminRequest;
import com.emrehalli.financeportal.market.api.dto.admin.InstrumentMappingAdminResponse;
import com.emrehalli.financeportal.market.api.mapper.MarketApiMapper;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.service.InstrumentRegistryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Admin endpoints for managing canonical instruments and provider mappings.
 */
@RestController
@RequestMapping("/api/v1/admin/instruments")
public class InstrumentAdminController {

    private final InstrumentRegistryService instrumentRegistryService;
    private final AppMessageSource appMessageSource;
    private final MarketApiMapper marketApiMapper;

    /**
     * Creates the controller.
     *
     * @param instrumentRegistryService instrument registry service
     * @param appMessageSource message source
     */
    public InstrumentAdminController(InstrumentRegistryService instrumentRegistryService,
                                     AppMessageSource appMessageSource,
                                     MarketApiMapper marketApiMapper) {
        this.instrumentRegistryService = instrumentRegistryService;
        this.appMessageSource = appMessageSource;
        this.marketApiMapper = marketApiMapper;
    }

    /**
     * Lists instruments with optional filters.
     *
     * @param type optional type filter
     * @param enabled optional enabled filter
     * Role: ADMIN.
     * @return wrapped instrument list
     */
    @GetMapping
    public ApiResponse<List<InstrumentAdminResponse>> getInstruments(@RequestParam(required = false) InstrumentType type,
                                                                     @RequestParam(required = false) Boolean enabled) {
        List<InstrumentAdminResponse> data = instrumentRegistryService.getInstruments(type, enabled).stream()
                .map(marketApiMapper::toInstrumentAdminResponse)
                .toList();
        return ApiResponse.<List<InstrumentAdminResponse>>builder()
                .success(true)
                .data(data)
                .message(appMessageSource.get("instrument.list.fetched"))
                .build();
    }

    /**
     * Creates a new canonical instrument.
     *
     * @param request create request
     * Role: ADMIN.
     * @return wrapped persisted instrument
     */
    @PostMapping
    public ApiResponse<InstrumentAdminResponse> createInstrument(@Valid @RequestBody InstrumentAdminRequest request) {
        InstrumentAdminResponse response = marketApiMapper.toInstrumentAdminResponse(instrumentRegistryService.createInstrument(
                request.symbol(),
                request.displayName(),
                request.type(),
                request.enabled()
        ));
        return ApiResponse.<InstrumentAdminResponse>builder()
                .success(true)
                .data(response)
                .message(appMessageSource.get("instrument.created"))
                .build();
    }

    /**
     * Updates an existing canonical instrument.
     *
     * @param id instrument identifier
     * @param request update request
     * Role: ADMIN.
     * @return wrapped updated instrument
     */
    @PutMapping("/{id}")
    public ApiResponse<InstrumentAdminResponse> updateInstrument(@PathVariable UUID id,
                                                                 @Valid @RequestBody InstrumentAdminRequest request) {
        InstrumentAdminResponse response = marketApiMapper.toInstrumentAdminResponse(instrumentRegistryService.updateInstrument(
                id,
                request.symbol(),
                request.displayName(),
                request.type(),
                request.enabled()
        ));
        return ApiResponse.<InstrumentAdminResponse>builder()
                .success(true)
                .data(response)
                .message(appMessageSource.get("instrument.updated"))
                .build();
    }

    /**
     * Soft-deletes an instrument by disabling it.
     *
     * @param id instrument identifier
     * Role: ADMIN.
     * @return wrapped success response
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteInstrument(@PathVariable UUID id) {
        instrumentRegistryService.disableInstrument(id);
        return ApiResponse.<Void>builder()
                .success(true)
                .data(null)
                .message(appMessageSource.get("instrument.deleted"))
                .build();
    }

    /**
     * Lists provider mappings for an instrument.
     *
     * @param id instrument identifier
     * Role: ADMIN.
     * @return wrapped mapping list
     */
    @GetMapping("/{id}/mappings")
    public ApiResponse<List<InstrumentMappingAdminResponse>> getMappings(@PathVariable UUID id) {
        List<InstrumentMappingAdminResponse> data = instrumentRegistryService.getMappings(id).stream()
                .map(marketApiMapper::toInstrumentMappingAdminResponse)
                .toList();
        return ApiResponse.<List<InstrumentMappingAdminResponse>>builder()
                .success(true)
                .data(data)
                .message(appMessageSource.get("instrument.mapping.list.fetched"))
                .build();
    }

    /**
     * Creates a provider mapping for an instrument.
     *
     * @param id instrument identifier
     * @param request create request
     * Role: ADMIN.
     * @return wrapped persisted mapping
     */
    @PostMapping("/{id}/mappings")
    public ApiResponse<InstrumentMappingAdminResponse> createMapping(@PathVariable UUID id,
                                                                     @Valid @RequestBody InstrumentMappingAdminRequest request) {
        InstrumentMappingAdminResponse response = marketApiMapper.toInstrumentMappingAdminResponse(instrumentRegistryService.createMapping(
                id,
                request.source(),
                request.externalSymbol(),
                request.priority(),
                request.refreshIntervalMinutes(),
                request.historyStartDate(),
                request.enabled()
        ));
        return ApiResponse.<InstrumentMappingAdminResponse>builder()
                .success(true)
                .data(response)
                .message(appMessageSource.get("instrument.mapping.created"))
                .build();
    }

    /**
     * Updates a provider mapping.
     *
     * @param id instrument identifier
     * @param mappingId mapping identifier
     * @param request update request
     * Role: ADMIN.
     * @return wrapped updated mapping
     */
    @PutMapping("/{id}/mappings/{mappingId}")
    public ApiResponse<InstrumentMappingAdminResponse> updateMapping(@PathVariable UUID id,
                                                                     @PathVariable UUID mappingId,
                                                                     @Valid @RequestBody InstrumentMappingAdminRequest request) {
        InstrumentMappingAdminResponse response = marketApiMapper.toInstrumentMappingAdminResponse(instrumentRegistryService.updateMapping(
                id,
                mappingId,
                request.source(),
                request.externalSymbol(),
                request.priority(),
                request.refreshIntervalMinutes(),
                request.historyStartDate(),
                request.enabled()
        ));
        return ApiResponse.<InstrumentMappingAdminResponse>builder()
                .success(true)
                .data(response)
                .message(appMessageSource.get("instrument.mapping.updated"))
                .build();
    }

    /**
     * Soft-deletes a mapping by disabling it.
     *
     * @param id instrument identifier
     * @param mappingId mapping identifier
     * Role: ADMIN.
     * @return wrapped success response
     */
    @DeleteMapping("/{id}/mappings/{mappingId}")
    public ApiResponse<Void> deleteMapping(@PathVariable UUID id, @PathVariable UUID mappingId) {
        instrumentRegistryService.disableMapping(id, mappingId);
        return ApiResponse.<Void>builder()
                .success(true)
                .data(null)
                .message(appMessageSource.get("instrument.mapping.deleted"))
                .build();
    }
}
