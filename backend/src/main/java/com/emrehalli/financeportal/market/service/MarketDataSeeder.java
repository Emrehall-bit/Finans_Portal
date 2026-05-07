package com.emrehalli.financeportal.market.service;

import com.emrehalli.financeportal.market.domain.enums.DataSource;
import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import com.emrehalli.financeportal.market.domain.enums.MappingRefreshStatus;
import com.emrehalli.financeportal.market.persistence.entity.MarketInstrumentEntity;
import com.emrehalli.financeportal.market.persistence.entity.MarketProviderMappingEntity;
import com.emrehalli.financeportal.market.persistence.repository.MarketInstrumentRepository;
import com.emrehalli.financeportal.market.persistence.repository.MarketProviderMappingRepository;
import com.emrehalli.financeportal.market.scheduler.MarketRefreshProperties;
import com.emrehalli.financeportal.market.support.SymbolNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds the market registry tables once when the database is empty.
 *
 * <p>Seed definitions are the single source of truth for initial instrument data.
 * To add a new instrument, append a {@code seed(...)} entry to {@link #seedDefinitions()}.
 * No other file needs to be modified.
 *
 * <p>History start dates default to 5 years for tradeable instruments and
 * 20 years for macro-economic indicators ({@link InstrumentType#MACRO_INDICATOR}).
 */
@Component
public class MarketDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MarketDataSeeder.class);

    /** Default history window for tradeable instruments (crypto, stock, forex, commodity). */
    private static final int DEFAULT_HISTORY_YEARS = 5;

    /**
     * Extended history window for macro-economic indicators.
     * EVDS series often have 20+ years of history available.
     */
    private static final int MACRO_HISTORY_YEARS = 20;

    private final MarketInstrumentRepository marketInstrumentRepository;
    private final MarketProviderMappingRepository marketProviderMappingRepository;
    private final MarketRefreshProperties marketRefreshProperties;
    private final SymbolNormalizer symbolNormalizer;
    private final Clock clock;

    /**
     * Creates the registry seeder.
     *
     * @param marketInstrumentRepository      instrument repository
     * @param marketProviderMappingRepository mapping repository
     * @param marketRefreshProperties         refresh policy properties
     * @param symbolNormalizer                symbol normalization helper
     * @param clock                           wall clock for default history start dates
     */
    public MarketDataSeeder(MarketInstrumentRepository marketInstrumentRepository,
                            MarketProviderMappingRepository marketProviderMappingRepository,
                            MarketRefreshProperties marketRefreshProperties,
                            SymbolNormalizer symbolNormalizer,
                            Clock clock) {
        this.marketInstrumentRepository = marketInstrumentRepository;
        this.marketProviderMappingRepository = marketProviderMappingRepository;
        this.marketRefreshProperties = marketRefreshProperties;
        this.symbolNormalizer = symbolNormalizer;
        this.clock = clock;
    }

    /**
     * Seeds market data only when both registry tables are empty.
     *
     * @param args application arguments
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        long instrumentCount = marketInstrumentRepository.count();
        long mappingCount = marketProviderMappingRepository.count();
        if (instrumentCount > 0L || mappingCount > 0L) {
            syncRefreshIntervalsFromConfig();
            log.info(
                    "Market registry seed skipped: instrumentCount={}, mappingCount={}",
                    instrumentCount,
                    mappingCount
            );
            return;
        }

        List<SeedDefinition> definitions = seedDefinitions();
        List<MarketInstrumentEntity> instruments = definitions.stream()
                .map(this::toInstrument)
                .toList();
        List<MarketInstrumentEntity> savedInstruments = marketInstrumentRepository.saveAll(instruments);

        List<MarketProviderMappingEntity> mappings = new ArrayList<>();
        for (int index = 0; index < definitions.size(); index++) {
            SeedDefinition definition = definitions.get(index);
            MarketInstrumentEntity instrument = savedInstruments.get(index);
            for (SeedMapping mapping : definition.mappings()) {
                mappings.add(toMapping(instrument, definition.type(), mapping));
            }
        }

        marketProviderMappingRepository.saveAll(mappings);
        log.info(
                "Market registry seeded: instrumentCount={}, mappingCount={}",
                savedInstruments.size(),
                mappings.size()
        );
    }

    private void syncRefreshIntervalsFromConfig() {
        List<MarketProviderMappingEntity> mappings = marketProviderMappingRepository.findAll();
        List<MarketProviderMappingEntity> changedMappings = mappings.stream()
                .filter(mapping -> mapping.getSource() != null)
                .filter(mapping -> mapping.getRefreshIntervalMinutes() != resolveRefreshInterval(mapping.getSource()))
                .peek(mapping -> mapping.setRefreshIntervalMinutes(resolveRefreshInterval(mapping.getSource())))
                .toList();

        if (changedMappings.isEmpty()) {
            return;
        }

        marketProviderMappingRepository.saveAll(changedMappings);
        log.info(
                "Market registry refresh intervals synced from config: updatedMappingCount={}",
                changedMappings.size()
        );
    }

    /**
     * Converts a seed definition into an instrument entity.
     *
     * @param definition seed definition
     * @return instrument entity
     */
    private MarketInstrumentEntity toInstrument(SeedDefinition definition) {
        MarketInstrumentEntity entity = new MarketInstrumentEntity();
        entity.setSymbol(symbolNormalizer.normalize(definition.symbol()).orElseThrow());
        entity.setDisplayName(definition.displayName());
        entity.setType(definition.type());
        entity.setEnabled(true);
        return entity;
    }

    /**
     * Converts a seed mapping into a provider mapping entity.
     * History start date is extended to {@value #MACRO_HISTORY_YEARS} years for
     * {@link InstrumentType#MACRO_INDICATOR} instruments, and defaults to
     * {@value #DEFAULT_HISTORY_YEARS} years for all others.
     *
     * @param instrument parent instrument
     * @param type       instrument type (used to resolve history window)
     * @param mapping    seed mapping
     * @return provider mapping entity
     */
    private MarketProviderMappingEntity toMapping(MarketInstrumentEntity instrument,
                                                  InstrumentType type,
                                                  SeedMapping mapping) {
        MarketProviderMappingEntity entity = new MarketProviderMappingEntity();
        entity.setInstrument(instrument);
        entity.setSource(mapping.source());
        entity.setExternalSymbol(mapping.externalSymbol());
        entity.setPriority(1);
        entity.setRefreshIntervalMinutes(resolveRefreshInterval(mapping.source()));
        entity.setHistoryStartDate(resolveHistoryStartDate(type));
        entity.setEnabled(true);
        entity.setLastRefreshStatus(MappingRefreshStatus.PENDING);
        return entity;
    }

    /**
     * Resolves the history start date based on instrument type.
     * Macro indicators use a 20-year window; all others use 5 years.
     *
     * @param type instrument type
     * @return history start date
     */
    private LocalDate resolveHistoryStartDate(InstrumentType type) {
        int years = (type == InstrumentType.MACRO_INDICATOR) ? MACRO_HISTORY_YEARS : DEFAULT_HISTORY_YEARS;
        return LocalDate.now(clock).minusYears(years);
    }

    /**
     * Resolves the configured refresh interval for a provider.
     *
     * @param source provider source
     * @return refresh interval in minutes
     */
    private int resolveRefreshInterval(DataSource source) {
        return marketRefreshProperties.policyFor(source)
                .map(MarketRefreshProperties.ProviderPolicy::getRefreshMinutes)
                .map(value -> (int) Math.max(value, 1L))
                .orElse(5);
    }

    /**
     * Builds bootstrap seed definitions for all supported instruments.
     *
     * <p><b>To add a new instrument:</b> append a {@code seed(...)} call here.
     * No other file needs to be modified.
     *
     * <p>Sections:
     * <ol>
     *   <li>Crypto — Binance USDT pairs (~44 symbols)</li>
     *   <li>BIST Stocks — BIST100 full list (~100 symbols, Yahoo Finance / BIST source)</li>
     *   <li>Forex — TCMB/EVDS major and minor currency pairs (~15 symbols)</li>
     *   <li>Commodity — Precious metals via EVDS (~3 symbols)</li>
     *   <li>Macro Indicators — EVDS interest rates and inflation series (~8 series)</li>
     * </ol>
     *
     * @return seed definitions
     */
    private List<SeedDefinition> seedDefinitions() {
        return List.of(

                // ─────────────────────────────────────────────────────────────────
                // CRYPTO — Binance USDT pairs
                // Source: Binance public REST API (no API key required for price/klines)
                // ─────────────────────────────────────────────────────────────────

                // Large cap
                seed("BTCUSDT",   "Bitcoin / Tether",          InstrumentType.COMMODITY, map(DataSource.BINANCE, "BTCUSDT")),
                seed("ETHUSDT",   "Ethereum / Tether",         InstrumentType.COMMODITY, map(DataSource.BINANCE, "ETHUSDT")),
                seed("BNBUSDT",   "BNB / Tether",              InstrumentType.COMMODITY, map(DataSource.BINANCE, "BNBUSDT")),
                seed("SOLUSDT",   "Solana / Tether",           InstrumentType.COMMODITY, map(DataSource.BINANCE, "SOLUSDT")),
                seed("XRPUSDT",   "XRP / Tether",              InstrumentType.COMMODITY, map(DataSource.BINANCE, "XRPUSDT")),
                seed("ADAUSDT",   "Cardano / Tether",          InstrumentType.COMMODITY, map(DataSource.BINANCE, "ADAUSDT")),
                seed("AVAXUSDT",  "Avalanche / Tether",        InstrumentType.COMMODITY, map(DataSource.BINANCE, "AVAXUSDT")),
                seed("DOGEUSDT",  "Dogecoin / Tether",         InstrumentType.COMMODITY, map(DataSource.BINANCE, "DOGEUSDT")),

                // Mid cap — layer 1 / layer 2
                seed("DOTUSDT",   "Polkadot / Tether",         InstrumentType.COMMODITY, map(DataSource.BINANCE, "DOTUSDT")),
                seed("MATICUSDT", "Polygon / Tether",          InstrumentType.COMMODITY, map(DataSource.BINANCE, "MATICUSDT")),
                seed("LINKUSDT",  "Chainlink / Tether",        InstrumentType.COMMODITY, map(DataSource.BINANCE, "LINKUSDT")),
                seed("LTCUSDT",   "Litecoin / Tether",         InstrumentType.COMMODITY, map(DataSource.BINANCE, "LTCUSDT")),
                seed("UNIUSDT",   "Uniswap / Tether",          InstrumentType.COMMODITY, map(DataSource.BINANCE, "UNIUSDT")),
                seed("ATOMUSDT",  "Cosmos / Tether",           InstrumentType.COMMODITY, map(DataSource.BINANCE, "ATOMUSDT")),
                seed("ETCUSDT",   "Ethereum Classic / Tether", InstrumentType.COMMODITY, map(DataSource.BINANCE, "ETCUSDT")),
                seed("XLMUSDT",   "Stellar / Tether",          InstrumentType.COMMODITY, map(DataSource.BINANCE, "XLMUSDT")),
                seed("ALGOUSDT",  "Algorand / Tether",         InstrumentType.COMMODITY, map(DataSource.BINANCE, "ALGOUSDT")),
                seed("NEARUSDT",  "NEAR Protocol / Tether",    InstrumentType.COMMODITY, map(DataSource.BINANCE, "NEARUSDT")),
                seed("FTMUSDT",   "Fantom / Tether",           InstrumentType.COMMODITY, map(DataSource.BINANCE, "FTMUSDT")),
                seed("SANDUSDT",  "The Sandbox / Tether",      InstrumentType.COMMODITY, map(DataSource.BINANCE, "SANDUSDT")),
                seed("MANAUSDT",  "Decentraland / Tether",     InstrumentType.COMMODITY, map(DataSource.BINANCE, "MANAUSDT")),
                seed("AXSUSDT",   "Axie Infinity / Tether",    InstrumentType.COMMODITY, map(DataSource.BINANCE, "AXSUSDT")),
                seed("GALAUSDT",  "Gala / Tether",             InstrumentType.COMMODITY, map(DataSource.BINANCE, "GALAUSDT")),
                seed("APEUSDT",   "ApeCoin / Tether",          InstrumentType.COMMODITY, map(DataSource.BINANCE, "APEUSDT")),
                seed("OPUSDT",    "Optimism / Tether",         InstrumentType.COMMODITY, map(DataSource.BINANCE, "OPUSDT")),
                seed("ARBUSDT",   "Arbitrum / Tether",         InstrumentType.COMMODITY, map(DataSource.BINANCE, "ARBUSDT")),
                seed("SHIBUSDT",  "Shiba Inu / Tether",        InstrumentType.COMMODITY, map(DataSource.BINANCE, "SHIBUSDT")),
                seed("TRXUSDT",   "TRON / Tether",             InstrumentType.COMMODITY, map(DataSource.BINANCE, "TRXUSDT")),
                seed("TONUSDT",   "Toncoin / Tether",          InstrumentType.COMMODITY, map(DataSource.BINANCE, "TONUSDT")),
                seed("SUIUSDT",   "Sui / Tether",              InstrumentType.COMMODITY, map(DataSource.BINANCE, "SUIUSDT")),
                seed("SEIUSDT",   "Sei / Tether",              InstrumentType.COMMODITY, map(DataSource.BINANCE, "SEIUSDT")),
                seed("INJUSDT",   "Injective / Tether",        InstrumentType.COMMODITY, map(DataSource.BINANCE, "INJUSDT")),
                seed("TIAUSDT",   "Celestia / Tether",         InstrumentType.COMMODITY, map(DataSource.BINANCE, "TIAUSDT")),

                // DeFi / infrastructure
                seed("AAVEUSDT",  "Aave / Tether",             InstrumentType.COMMODITY, map(DataSource.BINANCE, "AAVEUSDT")),
                seed("MKRUSDT",   "Maker / Tether",            InstrumentType.COMMODITY, map(DataSource.BINANCE, "MKRUSDT")),
                seed("CRVUSDT",   "Curve DAO / Tether",        InstrumentType.COMMODITY, map(DataSource.BINANCE, "CRVUSDT")),
                seed("SNXUSDT",   "Synthetix / Tether",        InstrumentType.COMMODITY, map(DataSource.BINANCE, "SNXUSDT")),
                seed("LDOUSDT",   "Lido DAO / Tether",         InstrumentType.COMMODITY, map(DataSource.BINANCE, "LDOUSDT")),
                seed("RENDERUSDT","Render / Tether",           InstrumentType.COMMODITY, map(DataSource.BINANCE, "RENDERUSDT")),
                seed("FETUSDT",   "Fetch.ai / Tether",         InstrumentType.COMMODITY, map(DataSource.BINANCE, "FETUSDT")),
                seed("WLDUSDT",   "Worldcoin / Tether",        InstrumentType.COMMODITY, map(DataSource.BINANCE, "WLDUSDT")),
                seed("JUPUSDT",   "Jupiter / Tether",          InstrumentType.COMMODITY, map(DataSource.BINANCE, "JUPUSDT")),
                seed("EIGENUSDT", "EigenLayer / Tether",       InstrumentType.COMMODITY, map(DataSource.BINANCE, "EIGENUSDT")),

                // ─────────────────────────────────────────────────────────────────
                // BIST STOCKS — BIST100 full list
                // Source: Yahoo Finance (.IS suffix) via BistDelayedClient
                // Symbols verified against BIST100 index composition (2025)
                // ─────────────────────────────────────────────────────────────────

                // Existing 27 — kept as-is
                seed("THYAO", "Turk Hava Yollari",      InstrumentType.STOCK, map(DataSource.BIST, "THYAO.IS")),
                seed("ASELS", "Aselsan",                InstrumentType.STOCK, map(DataSource.BIST, "ASELS.IS")),
                seed("GARAN", "Garanti BBVA",           InstrumentType.STOCK, map(DataSource.BIST, "GARAN.IS")),
                seed("AKBNK", "Akbank",                 InstrumentType.STOCK, map(DataSource.BIST, "AKBNK.IS")),
                seed("BIMAS", "Bim Birlesik Magazalar", InstrumentType.STOCK, map(DataSource.BIST, "BIMAS.IS")),
                seed("KCHOL", "Koc Holding",            InstrumentType.STOCK, map(DataSource.BIST, "KCHOL.IS")),
                seed("SAHOL", "Sabanci Holding",        InstrumentType.STOCK, map(DataSource.BIST, "SAHOL.IS")),
                seed("EREGL", "Eregli Demir Celik",     InstrumentType.STOCK, map(DataSource.BIST, "EREGL.IS")),
                seed("TUPRS", "Tupras",                 InstrumentType.STOCK, map(DataSource.BIST, "TUPRS.IS")),
                seed("FROTO", "Ford Otosan",            InstrumentType.STOCK, map(DataSource.BIST, "FROTO.IS")),
                seed("SISE",  "Sise Cam",               InstrumentType.STOCK, map(DataSource.BIST, "SISE.IS")),
                seed("PETKM", "Petkim",                 InstrumentType.STOCK, map(DataSource.BIST, "PETKM.IS")),
                seed("PGSUS", "Pegasus",                InstrumentType.STOCK, map(DataSource.BIST, "PGSUS.IS")),
                seed("TTKOM", "Turk Telekom",           InstrumentType.STOCK, map(DataSource.BIST, "TTKOM.IS")),
                seed("KOZAL", "Koza Altin",             InstrumentType.STOCK, map(DataSource.BIST, "KOZAL.IS")),
                seed("ISCTR", "Is Bankasi C",           InstrumentType.STOCK, map(DataSource.BIST, "ISCTR.IS")),
                seed("YKBNK", "Yapi Kredi",             InstrumentType.STOCK, map(DataSource.BIST, "YKBNK.IS")),
                seed("HALKB", "Halkbank",               InstrumentType.STOCK, map(DataSource.BIST, "HALKB.IS")),
                seed("VAKBN", "Vakifbank",              InstrumentType.STOCK, map(DataSource.BIST, "VAKBN.IS")),
                seed("TCELL", "Turkcell",               InstrumentType.STOCK, map(DataSource.BIST, "TCELL.IS")),
                seed("ARCLK", "Arcelik",                InstrumentType.STOCK, map(DataSource.BIST, "ARCLK.IS")),
                seed("TOASO", "Tofas",                  InstrumentType.STOCK, map(DataSource.BIST, "TOASO.IS")),
                seed("DOHOL", "Dogan Holding",          InstrumentType.STOCK, map(DataSource.BIST, "DOHOL.IS")),
                seed("ENKAI", "Enka Insaat",            InstrumentType.STOCK, map(DataSource.BIST, "ENKAI.IS")),
                seed("MGROS", "Migros",                 InstrumentType.STOCK, map(DataSource.BIST, "MGROS.IS")),
                seed("GUBRF", "Gubre Fabrikalari",      InstrumentType.STOCK, map(DataSource.BIST, "GUBRF.IS")),
                seed("HEKTS", "Hektas",                 InstrumentType.STOCK, map(DataSource.BIST, "HEKTS.IS")),
                seed("KRDMD", "Kardemir D",             InstrumentType.STOCK, map(DataSource.BIST, "KRDMD.IS")),
                seed("SASA",  "Sasa Polyester",         InstrumentType.STOCK, map(DataSource.BIST, "SASA.IS")),
                seed("ULKER", "Ulker Biskuvi",          InstrumentType.STOCK, map(DataSource.BIST, "ULKER.IS")),

                // BIST100 additions (~70 new symbols)
                seed("AEFES",  "Anadolu Efes",           InstrumentType.STOCK, map(DataSource.BIST, "AEFES.IS")),
                seed("AGESA",  "Agesa Emeklilik",        InstrumentType.STOCK, map(DataSource.BIST, "AGESA.IS")),
                seed("AGHOL",  "AG Anadolu Grubu",       InstrumentType.STOCK, map(DataSource.BIST, "AGHOL.IS")),
                seed("AHGAZ",  "Anadolu Gaz",            InstrumentType.STOCK, map(DataSource.BIST, "AHGAZ.IS")),
                seed("AKFGY",  "Akfen GYO",              InstrumentType.STOCK, map(DataSource.BIST, "AKFGY.IS")),
                seed("AKSEN",  "Aksa Enerji",            InstrumentType.STOCK, map(DataSource.BIST, "AKSEN.IS")),
                seed("ALARK",  "Alarko Holding",         InstrumentType.STOCK, map(DataSource.BIST, "ALARK.IS")),
                seed("ALBRK",  "Albaraka Turk",          InstrumentType.STOCK, map(DataSource.BIST, "ALBRK.IS")),
                seed("ALFAS",  "Alfa Solar",             InstrumentType.STOCK, map(DataSource.BIST, "ALFAS.IS")),
                seed("ANHYT",  "Anadolu Hayat",          InstrumentType.STOCK, map(DataSource.BIST, "ANHYT.IS")),
                seed("ANSGR",  "Anadolu Sigorta",        InstrumentType.STOCK, map(DataSource.BIST, "ANSGR.IS")),
                seed("ARASE",  "Arkas",                  InstrumentType.STOCK, map(DataSource.BIST, "ARASE.IS")),
                seed("ARDYZ",  "Ardahanli",              InstrumentType.STOCK, map(DataSource.BIST, "ARDYZ.IS")),
                seed("ARSAN",  "Arsan Tekstil",          InstrumentType.STOCK, map(DataSource.BIST, "ARSAN.IS")),
                seed("BERA",   "Bera Holding",           InstrumentType.STOCK, map(DataSource.BIST, "BERA.IS")),
                seed("BIOEN",  "Biotrend Enerji",        InstrumentType.STOCK, map(DataSource.BIST, "BIOEN.IS")),
                seed("BIZIM",  "Bizim Toptan",           InstrumentType.STOCK, map(DataSource.BIST, "BIZIM.IS")),
                seed("BRISA",  "Brisa Bridgestone",      InstrumentType.STOCK, map(DataSource.BIST, "BRISA.IS")),
                seed("BRYAT",  "BRY Insaat",             InstrumentType.STOCK, map(DataSource.BIST, "BRYAT.IS")),
                seed("BTCIM",  "Baticicim",              InstrumentType.STOCK, map(DataSource.BIST, "BTCIM.IS")),
                seed("CCOLA",  "Coca-Cola Icecek",       InstrumentType.STOCK, map(DataSource.BIST, "CCOLA.IS")),
                seed("CELHA",  "Celik Halat",            InstrumentType.STOCK, map(DataSource.BIST, "CELHA.IS")),
                seed("CEMTS",  "Cementir Holding",       InstrumentType.STOCK, map(DataSource.BIST, "CEMTS.IS")),
                seed("CIMSA",  "Cimsa Cimento",          InstrumentType.STOCK, map(DataSource.BIST, "CIMSA.IS")),
                seed("CLEBI",  "Celebi Hava Servisi",    InstrumentType.STOCK, map(DataSource.BIST, "CLEBI.IS")),
                seed("CMBTN",  "Cambaz Turizm",          InstrumentType.STOCK, map(DataSource.BIST, "CMBTN.IS")),
                seed("CWENE",  "Cengiz Enerji",          InstrumentType.STOCK, map(DataSource.BIST, "CWENE.IS")),
                seed("DEVA",   "Deva Holding",           InstrumentType.STOCK, map(DataSource.BIST, "DEVA.IS")),
                seed("DNISI",  "Doner Is",               InstrumentType.STOCK, map(DataSource.BIST, "DNISI.IS")),
                seed("DOAS",   "Dogus Otomotiv",         InstrumentType.STOCK, map(DataSource.BIST, "DOAS.IS")),
                seed("EGEEN",  "Ege Endustri",           InstrumentType.STOCK, map(DataSource.BIST, "EGEEN.IS")),
                seed("EKGYO",  "Emlak Konut GYO",        InstrumentType.STOCK, map(DataSource.BIST, "EKGYO.IS")),
                seed("ENJSA",  "Enerjisa Enerji",        InstrumentType.STOCK, map(DataSource.BIST, "ENJSA.IS")),
                seed("ERGLI",  "Eregli Demir A",         InstrumentType.STOCK, map(DataSource.BIST, "ERGLI.IS")),
                seed("EUPWR",  "Europower Enerji",       InstrumentType.STOCK, map(DataSource.BIST, "EUPWR.IS")),
                seed("FENER",  "Fenerbahce Futbol",      InstrumentType.STOCK, map(DataSource.BIST, "FENER.IS")),
                seed("FINBN",  "Finansbank",             InstrumentType.STOCK, map(DataSource.BIST, "FINBN.IS")),
                seed("FLAP",   "Flap Kongre",            InstrumentType.STOCK, map(DataSource.BIST, "FLAP.IS")),
                seed("GESAN",  "Gunes Sigorta",          InstrumentType.STOCK, map(DataSource.BIST, "GESAN.IS")),
                seed("GLYHO",  "Global Yatirim Holding", InstrumentType.STOCK, map(DataSource.BIST, "GLYHO.IS")),
                seed("GRSEL",  "Gursel Turizm",          InstrumentType.STOCK, map(DataSource.BIST, "GRSEL.IS")),
                seed("GSRAY",  "Galatasaray Sportif",    InstrumentType.STOCK, map(DataSource.BIST, "GSRAY.IS")),
                seed("IPEKE",  "Ipek Dogal Enerji",      InstrumentType.STOCK, map(DataSource.BIST, "IPEKE.IS")),
                seed("ISGYO",  "Is GYO",                 InstrumentType.STOCK, map(DataSource.BIST, "ISGYO.IS")),
                seed("ISGSY",  "Is Girisim Sermayesi",   InstrumentType.STOCK, map(DataSource.BIST, "ISGSY.IS")),
                seed("ISMEN",  "Is Yatirim",             InstrumentType.STOCK, map(DataSource.BIST, "ISMEN.IS")),
                seed("KARSN",  "Karsan",                 InstrumentType.STOCK, map(DataSource.BIST, "KARSN.IS")),
                seed("KCAER",  "Koc Tata",               InstrumentType.STOCK, map(DataSource.BIST, "KCAER.IS")),
                seed("KLNMA",  "Kalkinma Yatirim",       InstrumentType.STOCK, map(DataSource.BIST, "KLNMA.IS")),
                seed("KONTR",  "Kontrolmatik",           InstrumentType.STOCK, map(DataSource.BIST, "KONTR.IS")),
                seed("KONYA",  "Konya Cimento",          InstrumentType.STOCK, map(DataSource.BIST, "KONYA.IS")),
                seed("KOPOL",  "Kordsa",                 InstrumentType.STOCK, map(DataSource.BIST, "KOPOL.IS")),
                seed("KOTON",  "Koza Tekstil",           InstrumentType.STOCK, map(DataSource.BIST, "KOTON.IS")),
                seed("KZBGY",  "Kuzey Bati GYO",         InstrumentType.STOCK, map(DataSource.BIST, "KZBGY.IS")),
                seed("LOGO",   "Logo Yazilim",           InstrumentType.STOCK, map(DataSource.BIST, "LOGO.IS")),
                seed("MAVI",   "Mavi Giyim",             InstrumentType.STOCK, map(DataSource.BIST, "MAVI.IS")),
                seed("MPARK",  "MLP Saglik",             InstrumentType.STOCK, map(DataSource.BIST, "MPARK.IS")),
                seed("NETAS",  "Netas Telekomunikasyon", InstrumentType.STOCK, map(DataSource.BIST, "NETAS.IS")),
                seed("ODAS",   "Odas Elektrik",          InstrumentType.STOCK, map(DataSource.BIST, "ODAS.IS")),
                seed("OTKAR",  "Otokar",                 InstrumentType.STOCK, map(DataSource.BIST, "OTKAR.IS")),
                seed("OYAKC",  "Oyak Cimento",           InstrumentType.STOCK, map(DataSource.BIST, "OYAKC.IS")),
                seed("OZKGY",  "Ozak GYO",               InstrumentType.STOCK, map(DataSource.BIST, "OZKGY.IS")),
                seed("PARSN",  "Parsan",                 InstrumentType.STOCK, map(DataSource.BIST, "PARSN.IS")),
                seed("PGOLD",  "Park Elektrik",          InstrumentType.STOCK, map(DataSource.BIST, "PGOLD.IS")),
                seed("QUAGR",  "Qua Granite",            InstrumentType.STOCK, map(DataSource.BIST, "QUAGR.IS")),
                seed("RTALB",  "RT Albaraka",            InstrumentType.STOCK, map(DataSource.BIST, "RTALB.IS")),
                seed("SDTTR",  "SDT Uzay",               InstrumentType.STOCK, map(DataSource.BIST, "SDTTR.IS")),
                seed("SKBNK",  "Sekerbank",              InstrumentType.STOCK, map(DataSource.BIST, "SKBNK.IS")),
                seed("SMART",  "Smart Gunes",            InstrumentType.STOCK, map(DataSource.BIST, "SMART.IS")),
                seed("SNPAM",  "Sanovel Ilac",           InstrumentType.STOCK, map(DataSource.BIST, "SNPAM.IS")),
                seed("SOKM",   "Sok Marketler",          InstrumentType.STOCK, map(DataSource.BIST, "SOKM.IS")),
                seed("SUNTK",  "Suntek",                 InstrumentType.STOCK, map(DataSource.BIST, "SUNTK.IS")),
                seed("TABGD",  "TAB Gida",               InstrumentType.STOCK, map(DataSource.BIST, "TABGD.IS")),
                seed("TRGYO",  "Torunlar GYO",           InstrumentType.STOCK, map(DataSource.BIST, "TRGYO.IS")),
                seed("TSKB",   "TSKB",                   InstrumentType.STOCK, map(DataSource.BIST, "TSKB.IS")),
                seed("TTRAK",  "Turk Traktor",           InstrumentType.STOCK, map(DataSource.BIST, "TTRAK.IS")),
                seed("TURSG",  "Turk Sigorta",           InstrumentType.STOCK, map(DataSource.BIST, "TURSG.IS")),
                seed("USDTR",  "Ustas Ambalaj",          InstrumentType.STOCK, map(DataSource.BIST, "USDTR.IS")),
                seed("VESTL",  "Vestel",                 InstrumentType.STOCK, map(DataSource.BIST, "VESTL.IS")),
                seed("YEOTK",  "Yeo Teknoloji",          InstrumentType.STOCK, map(DataSource.BIST, "YEOTK.IS")),
                seed("YGYO",   "Yeni Gimat GYO",         InstrumentType.STOCK, map(DataSource.BIST, "YGYO.IS")),
                seed("ZRGYO",  "Zurlu GYO",              InstrumentType.STOCK, map(DataSource.BIST, "ZRGYO.IS")),

                // ─────────────────────────────────────────────────────────────────
                // FOREX — TCMB/EVDS currency pairs
                // Source: EVDS REST API (TP.DK.* series = buying rate)
                // Existing 11 kept as-is, 4 new added
                // ─────────────────────────────────────────────────────────────────

                seed("USDTRY", "USD / TRY", InstrumentType.FOREX, map(DataSource.EVDS, "TP.DK.USD.A")),
                seed("EURTRY", "EUR / TRY", InstrumentType.FOREX, map(DataSource.EVDS, "TP.DK.EUR.A")),
                seed("GBPTRY", "GBP / TRY", InstrumentType.FOREX, map(DataSource.EVDS, "TP.DK.GBP.A")),
                seed("CHFTRY", "CHF / TRY", InstrumentType.FOREX, map(DataSource.EVDS, "TP.DK.CHF.A")),
                seed("JPYTRY", "JPY / TRY", InstrumentType.FOREX, map(DataSource.EVDS, "TP.DK.JPY.A")),
                seed("AUDTRY", "AUD / TRY", InstrumentType.FOREX, map(DataSource.EVDS, "TP.DK.AUD.A")),
                seed("CNYTRY", "CNY / TRY", InstrumentType.FOREX, map(DataSource.EVDS, "TP.DK.CNY.A.YTL")),
                seed("NOKTRY", "NOK / TRY", InstrumentType.FOREX, map(DataSource.EVDS, "TP.DK.NOK.A")),
                seed("SEKTRY", "SEK / TRY", InstrumentType.FOREX, map(DataSource.EVDS, "TP.DK.SEK.A")),
                seed("DKKTRY", "DKK / TRY", InstrumentType.FOREX, map(DataSource.EVDS, "TP.DK.DKK.A")),
                seed("SARTRY", "SAR / TRY", InstrumentType.FOREX, map(DataSource.EVDS, "TP.DK.SAR.A")),
                // New forex pairs
                seed("CADTRY", "CAD / TRY", InstrumentType.FOREX, map(DataSource.EVDS, "TP.DK.CAD.A")),
                seed("KWDTRY", "KWD / TRY", InstrumentType.FOREX, map(DataSource.EVDS, "TP.DK.KWD.A")),
                seed("AEDTRY", "AED / TRY", InstrumentType.FOREX, map(DataSource.EVDS, "TP.DK.AED.A")),
                seed("ROBTRY", "RON / TRY", InstrumentType.FOREX, map(DataSource.EVDS, "TP.DK.RON.A")),

                // TEFAS mutual funds
                seed("AFT", "Ak Portfoy Yeni Teknolojiler Yabanci Hisse Senedi Fonu", InstrumentType.FUND, map(DataSource.TEFAS, "AFT")),
                seed("AFA", "Ak Portfoy Amerika Yabanci Hisse Senedi Fonu", InstrumentType.FUND, map(DataSource.TEFAS, "AFA")),
                seed("MAC", "Istanbul Portfoy Birinci Degisken Fon", InstrumentType.FUND, map(DataSource.TEFAS, "MAC")),
                seed("IPB", "Is Portfoy Birinci Degisken Fon", InstrumentType.FUND, map(DataSource.TEFAS, "IPB")),
                seed("IIH", "Is Portfoy BIST 100 Disi Sirketler Hisse Senedi Fonu", InstrumentType.FUND, map(DataSource.TEFAS, "IIH")),
                seed("NNF", "Neo Portfoy Birinci Degisken Fon", InstrumentType.FUND, map(DataSource.TEFAS, "NNF")),
                seed("YAS", "Yapi Kredi Portfoy Koctas Ikinci Degisken Fon", InstrumentType.FUND, map(DataSource.TEFAS, "YAS")),
                seed("DVT", "Deniz Portfoy Onuncu Serbest Fon", InstrumentType.FUND, map(DataSource.TEFAS, "DVT")),
                seed("GMR", "Garanti Portfoy Birinci Para Piyasasi Fonu", InstrumentType.FUND, map(DataSource.TEFAS, "GMR")),
                seed("KPH", "Kuveyt Turk Portfoy Para Piyasasi Katilim Fonu", InstrumentType.FUND, map(DataSource.TEFAS, "KPH")),
                seed("CPU", "Citius Portfoy Serbest Fon", InstrumentType.FUND, map(DataSource.TEFAS, "CPU")),
                seed("SAS", "Strateji Portfoy Birinci Fon Sepeti Fonu", InstrumentType.FUND, map(DataSource.TEFAS, "SAS")),
                seed("GSP", "Garanti Portfoy Altin Fonu", InstrumentType.FUND, map(DataSource.TEFAS, "GSP")),
                seed("TCD", "Tacirler Portfoy Degisken Fon", InstrumentType.FUND, map(DataSource.TEFAS, "TCD")),
                seed("YKT", "Yapi Kredi Portfoy Altin Fonu", InstrumentType.FUND, map(DataSource.TEFAS, "YKT")),

                // ─────────────────────────────────────────────────────────────────
                // COMMODITY — Precious metals
                // Source: EVDS (TP.MK.* = Istanbul Gold Exchange)
                // ─────────────────────────────────────────────────────────────────


                // ─────────────────────────────────────────────────────────────────
                // MACRO INDICATORS — EVDS economic series
                // Source: EVDS REST API
                // historyStartDate = now - 20 years (resolved in toMapping)
                // Refresh cadence: daily (data published weekly/monthly by TCMB)
                // ─────────────────────────────────────────────────────────────────

                // Policy interest rate (TCMB 1-week repo rate)
                // Consumer price index (CPI/TUFE monthly)
                // Producer price index (PPI/ÜFE monthly)
                // USD/TRY buying rate — central bank official (daily, complements forex module)
                // EUR/TRY buying rate — central bank official (daily)
                // BIST-100 index level (daily)
                // Current account balance (monthly, USD)
                // Unemployment rate (quarterly, %)
                seed("TCMBTUFE_AYLIK",  "TUFE Aylik Degisim (%)",         InstrumentType.MACRO_INDICATOR, map(DataSource.EVDS, "TP.TUKFIY2025.GENEL-1")),
                seed("TCMBTUFE_YILLIK", "TUFE Yillik Degisim (%)",        InstrumentType.MACRO_INDICATOR, map(DataSource.EVDS, "TP.TUKFIY2025.GENEL-3")),
                seed("TCMBUFE_AYLIK",   "UFE Aylik Degisim (%)",          InstrumentType.MACRO_INDICATOR, map(DataSource.EVDS, "TP.TUFE1YI.T1-1")),
                seed("TCMBUFE_YILLIK",  "UFE Yillik Degisim (%)",         InstrumentType.MACRO_INDICATOR, map(DataSource.EVDS, "TP.TUFE1YI.T1-3")),
                seed("TCMBISSIZLIK",    "Issizlik Orani (%)",             InstrumentType.MACRO_INDICATOR, map(DataSource.EVDS, "TP.TIG08")),
                seed("TCMBFAIZ",        "TCMB Politika Faizi (%)",        InstrumentType.MACRO_INDICATOR, map(DataSource.EVDS, "TP.BISPOLFAIZ.TUR")),
                seed("TCMBMEVFAIZ",     "TL Mevduat Faizi (%)",           InstrumentType.MACRO_INDICATOR, map(DataSource.EVDS, "TP.TRY.MT06")),
                seed("BIST_BILESIK",    "BIST Bilesik Endeks",            InstrumentType.MACRO_INDICATOR, map(DataSource.EVDS, "TP.MK.F.BILESIK"))
        );
    }

    /**
     * Creates a seed definition helper.
     *
     * @param symbol      canonical symbol
     * @param displayName display name
     * @param type        instrument type
     * @param mappings    provider mappings
     * @return seed definition
     */
    private SeedDefinition seed(String symbol, String displayName, InstrumentType type, SeedMapping... mappings) {
        return new SeedDefinition(symbol, displayName, type, List.of(mappings));
    }

    /**
     * Creates a seed mapping helper.
     *
     * @param source         provider source
     * @param externalSymbol provider symbol
     * @return seed mapping
     */
    private SeedMapping map(DataSource source, String externalSymbol) {
        return new SeedMapping(source, externalSymbol);
    }

    /**
     * Immutable bootstrap seed definition.
     *
     * @param symbol      canonical symbol
     * @param displayName display name
     * @param type        instrument type
     * @param mappings    provider mappings
     */
    private record SeedDefinition(String symbol, String displayName, InstrumentType type,
                                  List<SeedMapping> mappings) {
    }

    /**
     * Immutable bootstrap seed mapping.
     *
     * @param source         provider source
     * @param externalSymbol provider symbol
     */
    private record SeedMapping(DataSource source, String externalSymbol) {
    }
}
