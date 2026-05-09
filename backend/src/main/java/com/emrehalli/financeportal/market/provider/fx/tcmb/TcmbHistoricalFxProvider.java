package com.emrehalli.financeportal.market.provider.fx.tcmb;

import com.emrehalli.financeportal.market.provider.fx.tcmb.client.TcmbEvdsClient;
import com.emrehalli.financeportal.market.provider.fx.tcmb.dto.TcmbEvdsResponse;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class TcmbHistoricalFxProvider {

    static final int CHUNK_DAYS = 900;

    private final TcmbEvdsClient tcmbEvdsClient;

    public TcmbHistoricalFxProvider(TcmbEvdsClient tcmbEvdsClient) {
        this.tcmbEvdsClient = tcmbEvdsClient;
    }

    public TcmbEvdsResponse fetch(String seriesCode, LocalDate startDate, LocalDate endDate) {
        return tcmbEvdsClient.fetch(seriesCode, startDate, endDate);
    }

    public List<Map<String, Object>> fetchHistoricalChunked(
            List<String> seriesCodes,
            LocalDate startDate,
            LocalDate endDate
    ) {
        List<Map<String, Object>> aggregatedItems = new ArrayList<>();
        LocalDate currentStart = startDate;

        while (!currentStart.isAfter(endDate)) {
            LocalDate currentEnd = min(currentStart.plusDays(CHUNK_DAYS - 1L), endDate);
            TcmbEvdsResponse response = tcmbEvdsClient.fetch(seriesCodes, currentStart, currentEnd);

            if (response.getItems() != null) {
                aggregatedItems.addAll(response.getItems());
            }

            currentStart = currentEnd.plusDays(1);
        }

        return aggregatedItems;
    }

    private LocalDate min(LocalDate left, LocalDate right) {
        return left.isBefore(right) ? left : right;
    }
}
