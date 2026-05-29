package com.emrehalli.financeportal.admin.markettape.repository;

import com.emrehalli.financeportal.admin.markettape.entity.MarketTapeSymbol;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MarketTapeSymbolRepository extends JpaRepository<MarketTapeSymbol, Long> {

    List<MarketTapeSymbol> findAllByEnabledTrueOrderByDisplayOrderAscIdAsc();
}




