package com.emrehalli.financeportal.market.provider.tcmb.mapper;

import com.emrehalli.financeportal.market.dto.common.MarketDataDto;
import com.emrehalli.financeportal.market.provider.tcmb.dto.EvdsResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class EvdsMapper {

    private static final Logger logger = LogManager.getLogger(EvdsMapper.class);

    public List<MarketDataDto> map(EvdsResponse response) {
        List<MarketDataDto> result = new ArrayList<>();

        // Senin birleştirdiğin temiz if yapısı ve geri eklenen log
        if (response == null || response.getItems() == null || response.getItems().isEmpty()) {
            logger.warn("EVDS response or items are null/empty. Returning empty market data list.");
            return result;
        }

        Map<String, Object> lastValidItem = null;

        // Son geçerli tarihi bulmak için sondan başa doğru tarama
        for (int i = response.getItems().size() - 1; i >= 0; i--) {
            Map<String, Object> item = response.getItems().get(i);

            Object usdValue = item.get("TP_DK_USD_A");
            Object eurValue = item.get("TP_DK_EUR_A");

            if (usdValue != null && eurValue != null) {
                lastValidItem = item;
                break;
            }
        }

        // Geçerli veri bulunamazsa sessizce patlamasını önleyen log
        if (lastValidItem == null) {
            logger.warn("No valid USD/EUR data found in EVDS items. Returning empty list.");
            return result;
        }

        result.add(
                MarketDataDto.builder()
                        .symbol("USDTRY")
                        .name("USD / TRY")
                        .price(String.valueOf(lastValidItem.get("TP_DK_USD_A")))
                        .source("EVDS")
                        .lastUpdated(LocalDateTime.now().toString())
                        .build()
        );

        result.add(
                MarketDataDto.builder()
                        .symbol("EURTRY")
                        .name("EUR / TRY")
                        .price(String.valueOf(lastValidItem.get("TP_DK_EUR_A")))
                        .source("EVDS")
                        .lastUpdated(LocalDateTime.now().toString())
                        .build()
        );

        logger.debug("EVDS mapping completed successfully. Mapped 2 items (USDTRY, EURTRY).");

        return result;
    }
}