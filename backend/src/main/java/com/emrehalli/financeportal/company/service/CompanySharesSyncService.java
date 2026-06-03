package com.emrehalli.financeportal.company.service;

import com.emrehalli.financeportal.company.domain.entity.CompanyProfile;
import com.emrehalli.financeportal.company.persistence.CompanyProfileRepository;
import com.emrehalli.financeportal.market.provider.stock.YahooFinanceStockProvider;
import com.emrehalli.financeportal.market.provider.stock.dto.StockPriceDto;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Yahoo Finance'ten sharesOutstanding çekerek eksik company_profiles kayıtlarını günceller.
 * Mevcut (null olmayan) shares_outstanding değerlerine dokunmaz.
 */
@Service
public class CompanySharesSyncService {

    private static final Logger log = LogManager.getLogger(CompanySharesSyncService.class);

    private final YahooFinanceStockProvider yahooProvider;
    private final CompanyProfileRepository profileRepository;

    public CompanySharesSyncService(YahooFinanceStockProvider yahooProvider,
                                    CompanyProfileRepository profileRepository) {
        this.yahooProvider = yahooProvider;
        this.profileRepository = profileRepository;
    }

    public record SharesSyncResult(
            int updated,
            int skippedHasData,
            int notInYahoo,
            List<String> updatedTickers,
            List<String> stillMissing
    ) {}

    @Transactional
    public SharesSyncResult syncSharesFromYahoo() {
        // Yahoo'dan fiyat + sharesOutstanding çek (mevcut batch endpoint)
        List<StockPriceDto> prices;
        try {
            prices = yahooProvider.fetch();
        } catch (Exception e) {
            log.error("shares.sync.yahooFailed reason={}", e.getMessage());
            throw e;
        }

        // ticker → sharesOutstanding (hem doğrudan hem türetilmiş)
        Map<String, BigDecimal> sharesMap = prices.stream()
                .filter(p -> p.sharesOutstanding() != null
                          && p.sharesOutstanding().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toMap(
                        p -> p.symbol().toUpperCase(Locale.ROOT),
                        StockPriceDto::sharesOutstanding,
                        (a, b) -> a));

        log.info("shares.sync.yahooFetched total={} withShares={}",
                prices.size(), sharesMap.size());

        int updated = 0, skippedHasData = 0, notInYahoo = 0;
        List<String> updatedTickers = new ArrayList<>();
        List<String> stillMissing = new ArrayList<>();

        for (CompanyProfile profile : profileRepository.findByActiveTrue()) {
            String ticker = profile.getTickerCode().toUpperCase(Locale.ROOT);
            BigDecimal yahooShares = sharesMap.get(ticker);

            if (yahooShares == null) {
                notInYahoo++;
                stillMissing.add(ticker);
                continue;
            }

            if (profile.getSharesOutstanding() != null
                    && profile.getSharesOutstanding().compareTo(BigDecimal.ZERO) > 0) {
                skippedHasData++;
                continue;
            }

            profile.setSharesOutstanding(yahooShares);
            profile.setUpdatedAt(OffsetDateTime.now());
            profileRepository.save(profile);
            updatedTickers.add(ticker);
            updated++;
            log.info("shares.sync.updated ticker={} shares={}", ticker, yahooShares.toPlainString());
        }

        log.info("shares.sync.done updated={} skippedHasData={} notInYahoo={}",
                updated, skippedHasData, notInYahoo);

        return new SharesSyncResult(updated, skippedHasData, notInYahoo, updatedTickers, stillMissing);
    }
}
