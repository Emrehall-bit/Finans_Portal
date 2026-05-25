package com.emrehalli.financeportal.market.persistence;

import com.emrehalli.financeportal.market.domain.entity.MacroIndicator;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MacroIndicatorRepository extends JpaRepository<MacroIndicator, Long> {

    Optional<MacroIndicator> findByCode(String code);
}



