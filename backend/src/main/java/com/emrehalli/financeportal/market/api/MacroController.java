package com.emrehalli.financeportal.market.api;

import com.emrehalli.financeportal.market.domain.enums.MacroFrequency;
import com.emrehalli.financeportal.market.domain.enums.MacroSourceName;
import com.emrehalli.financeportal.market.domain.enums.MacroUnit;
import com.emrehalli.financeportal.market.domain.enums.MacroValueType;
import com.emrehalli.financeportal.market.persistence.MacroIndicatorRepository;
import com.emrehalli.financeportal.market.persistence.MacroObservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/markets/macro")
@RequiredArgsConstructor
public class MacroController {

    private final MacroIndicatorRepository indicatorRepository;
    private final MacroObservationRepository observationRepository;

    @GetMapping("/indicators")
    public List<MacroIndicatorResponse> listIndicators() {
        return indicatorRepository.findAll().stream()
                .map(i -> new MacroIndicatorResponse(
                        i.getCode(), i.getName(), i.getSource(),
                        i.getFrequency(), i.getUnit(), i.isActive()))
                .toList();
    }

    @GetMapping("/indicators/{code}/observations")
    public List<MacroObservationResponse> getObservations(
            @PathVariable String code,
            @RequestParam MacroValueType valueType) {
        return observationRepository
                .findByIndicatorCodeAndValueTypeOrderByObservationDateAsc(code, valueType)
                .stream()
                .map(o -> new MacroObservationResponse(
                        o.getPeriodLabel(), o.getObservationDate(),
                        o.getValue(), o.getValueType(), o.getSource()))
                .toList();
    }

    private record MacroIndicatorResponse(
            String code,
            String name,
            MacroSourceName source,
            MacroFrequency frequency,
            MacroUnit unit,
            boolean active
    ) {
    }

    private record MacroObservationResponse(
            String periodLabel,
            LocalDate observationDate,
            BigDecimal value,
            MacroValueType valueType,
            MacroSourceName source
    ) {
    }
}




