package com.emrehalli.financeportal.market.persistence;

import com.emrehalli.financeportal.market.domain.entity.MacroIndicator;
import com.emrehalli.financeportal.market.domain.entity.MacroObservation;
import com.emrehalli.financeportal.market.domain.enums.MacroValueType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface MacroObservationRepository extends JpaRepository<MacroObservation, Long> {

    List<MacroObservation> findByIndicatorCodeAndValueTypeOrderByObservationDateAsc(
            String code, MacroValueType valueType);

    List<MacroObservation> findByIndicatorCodeAndValueTypeAndObservationDateBetweenOrderByObservationDateAsc(
            String code, MacroValueType valueType, LocalDate from, LocalDate to);

    boolean existsByIndicatorAndObservationDateAndValueType(
            MacroIndicator indicator, LocalDate observationDate, MacroValueType valueType);
}




