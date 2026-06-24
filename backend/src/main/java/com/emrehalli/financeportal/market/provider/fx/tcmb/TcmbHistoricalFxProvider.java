package com.emrehalli.financeportal.market.provider.fx.tcmb;

import com.emrehalli.financeportal.market.exception.DataProviderException;
import com.emrehalli.financeportal.market.provider.fx.tcmb.client.TcmbEvdsClient;
import com.emrehalli.financeportal.market.provider.fx.tcmb.dto.TcmbEvdsResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
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
            if (currentEnd.isBefore(currentStart)) {
                log.error("TCMB historical chunk loop guard triggered. currentStart={}, currentEnd={}, endDate={}",
                        currentStart, currentEnd, endDate);
                break;
            }

            try {
                TcmbEvdsResponse response = tcmbEvdsClient.fetch(seriesCodes, currentStart, currentEnd);
                if (response.getItems() != null) {
                    aggregatedItems.addAll(response.getItems());
                }
            } catch (DataProviderException exception) {
                log.error("Skipping TCMB historical chunk due to fetch error. chunkStart={}, chunkEnd={}, seriesCodesCount={}",
                        currentStart, currentEnd, seriesCodes == null ? 0 : seriesCodes.size(), exception);
            }

            LocalDate nextStart = currentEnd.plusDays(1);
            if (!nextStart.isAfter(currentStart)) {
                log.error("TCMB historical chunk loop did not advance. currentStart={}, currentEnd={}, nextStart={}",
                        currentStart, currentEnd, nextStart);
                break;
            }

            currentStart = nextStart;
        }

        return aggregatedItems;
    }

    private LocalDate min(LocalDate left, LocalDate right) {
        return left.isBefore(right) ? left : right;
    }
}

