package com.emrehalli.financeportal.news.generator;

import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Enstrüman tipi ve {@link SystemNewsScenario}'ya göre düzenlenmiş şablon bankasından sistem üretimi
 * haberler için Türkçe başlık ve özet üreten bileşen. Kripto, FX ve emtia sembolleri için okunabilir
 * görüntüleme adlarını çözümler ve enstrümana özgü yer tutucuları rastgele seçilen şablona yerleştirir;
 * özel şablon bulunmadığında genel şablona geri döner.
 */
@Component
public class SystemNewsTextGenerator {

    private static final Map<String, String> CRYPTO_DISPLAY_NAMES = Map.ofEntries(
            Map.entry("BTC", "Bitcoin"),
            Map.entry("ETH", "Ethereum"),
            Map.entry("BNB", "BNB"),
            Map.entry("SOL", "Solana"),
            Map.entry("XRP", "XRP"),
            Map.entry("ADA", "Cardano"),
            Map.entry("DOGE", "Dogecoin"),
            Map.entry("AVAX", "Avalanche"),
            Map.entry("DOT", "Polkadot"),
            Map.entry("LINK", "Chainlink"),
            Map.entry("MATIC", "Polygon"),
            Map.entry("UNI", "Uniswap"),
            Map.entry("LTC", "Litecoin"),
            Map.entry("ATOM", "Cosmos"),
            Map.entry("TRX", "TRON")
    );

    private static final Map<String, String> FX_DISPLAY_NAMES = Map.ofEntries(
            Map.entry("USD", "ABD Doları"),
            Map.entry("EUR", "Euro"),
            Map.entry("GBP", "Sterlin"),
            Map.entry("JPY", "Japon Yeni"),
            Map.entry("CHF", "İsviçre Frangı"),
            Map.entry("AUD", "Avustralya Doları"),
            Map.entry("CAD", "Kanada Doları"),
            Map.entry("RUB", "Rus Rublesi"),
            Map.entry("CNY", "Çin Yuanı"),
            Map.entry("AZN", "Azerbaycan Manatı"),
            Map.entry("KWD", "Kuveyt Dinarı"),
            Map.entry("QAR", "Katar Riyali"),
            Map.entry("KRW", "Güney Kore Wonu"),
            Map.entry("SAR", "Suudi Riyali")
    );

    private static final Map<String, String> COMMODITY_DISPLAY_NAMES = Map.ofEntries(
            Map.entry("GOLD_USD", "Altın"),
            Map.entry("GOLD_TRY", "Altın"),
            Map.entry("SILVER_USD", "Gümüş"),
            Map.entry("SILVER_TRY", "Gümüş"),
            Map.entry("BRENT", "Brent Petrol"),
            Map.entry("WTI", "Ham Petrol"),
            Map.entry("NATGAS", "Doğalgaz")
    );

    private record Templates(List<String> titles, List<String> summaries) {}

    private static final Map<InstrumentType, Map<SystemNewsScenario, Templates>> TEMPLATE_MAP =
            new EnumMap<>(InstrumentType.class);

    static {
        TEMPLATE_MAP.put(InstrumentType.CRYPTO, buildCryptoTemplates());
        TEMPLATE_MAP.put(InstrumentType.STOCK, buildStockTemplates());
        TEMPLATE_MAP.put(InstrumentType.FX, buildFxTemplates());
        TEMPLATE_MAP.put(InstrumentType.COMMODITY, buildCommodityTemplates());
        TEMPLATE_MAP.put(InstrumentType.FUND, buildFundTemplates());
    }

    /**
     * Ham sembolü kripto, FX ve emtia enstrümanları için insan tarafından okunabilir görüntüleme
     * adına dönüştürür; kripto için yaygın işlem çifti son eklerini çıkarır. Diğer tipler veya
     * bilinmeyen semboller için orijinal sembol değiştirilmeden döndürülür.
     *
     * @param symbol enstrümanın ham sembolü
     * @param type   enstrüman kategorisi
     * @return okunabilir görüntüleme adı; eşleşme yoksa orijinal sembol
     */
    public String resolveDisplayName(String symbol, InstrumentType type) {
        return switch (type) {
            case CRYPTO -> CRYPTO_DISPLAY_NAMES.getOrDefault(stripTradingPair(symbol), symbol);
            case FX -> FX_DISPLAY_NAMES.getOrDefault(symbol.toUpperCase(), symbol);
            case COMMODITY -> COMMODITY_DISPLAY_NAMES.getOrDefault(symbol.toUpperCase(), symbol);
            default -> symbol;
        };
    }

    /**
     * Fiyat değişim bağlamı için enstrüman tipi ve senaryoyla eşleşen rastgele bir başlık şablonu
     * seçerek yer tutucularını doldurur ve başlık üretir.
     *
     * @param ctx enstrümanı ve hareketi tanımlayan fiyat değişim bağlamı
     * @return yayınlanmaya hazır başlık
     */
    public String generateTitle(PriceChangeContext ctx) {
        Templates t = getTemplates(ctx.instrumentType(), ctx.scenario());
        String raw = pick(t.titles());
        return interpolate(raw, ctx);
    }

    /**
     * Fiyat değişim bağlamı için enstrüman tipi ve senaryoyla eşleşen rastgele bir özet şablonu
     * seçerek yer tutucularını doldurur ve özet paragrafı üretir.
     *
     * @param ctx enstrümanı ve hareketi tanımlayan fiyat değişim bağlamı
     * @return yayınlanmaya hazır özet
     */
    public String generateSummary(PriceChangeContext ctx) {
        Templates t = getTemplates(ctx.instrumentType(), ctx.scenario());
        String raw = pick(t.summaries());
        return interpolate(raw, ctx);
    }

    private Templates getTemplates(InstrumentType type, SystemNewsScenario scenario) {
        Map<SystemNewsScenario, Templates> byType = TEMPLATE_MAP.get(type);
        if (byType == null) {
            return fallbackTemplates(scenario);
        }
        Templates t = byType.get(scenario);
        return t != null ? t : fallbackTemplates(scenario);
    }

    private String interpolate(String template, PriceChangeContext ctx) {
        return template
                .replace("{name}", ctx.displayName())
                .replace("{symbol}", ctx.symbol())
                .replace("{change}", ctx.formattedChange());
    }

    private String pick(List<String> list) {
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    private String stripTradingPair(String symbol) {
        for (String suffix : List.of("USDT", "BUSD", "USDC", "TRY", "BTC")) {
            if (symbol.toUpperCase().endsWith(suffix) && symbol.length() > suffix.length()) {
                return symbol.substring(0, symbol.length() - suffix.length()).toUpperCase();
            }
        }
        return symbol.toUpperCase();
    }

    private Templates fallbackTemplates(SystemNewsScenario scenario) {
        if (scenario.isRise()) {
            return new Templates(
                    List.of("{name} dikkat çekici yükseliş kaydetti"),
                    List.of("{name}, son işlemlerde yüzde {change} değer kazanarak yatırımcıların gündemine girdi.")
            );
        }
        return new Templates(
                List.of("{name} değer kaybında"),
                List.of("{name}, son işlemlerde yüzde {change} değer kaybıyla baskılı seyir izliyor.")
        );
    }

    // -------------------------------------------------------------------------
    // Template definitions
    // -------------------------------------------------------------------------

    private static Map<SystemNewsScenario, Templates> buildCryptoTemplates() {
        Map<SystemNewsScenario, Templates> m = new EnumMap<>(SystemNewsScenario.class);

        m.put(SystemNewsScenario.STRONG_RISE, new Templates(
                List.of(
                        "{name}'de güçlü alım dalgası dikkat çekiyor",
                        "{name} yatırımcıların radarına girdi",
                        "{name} momentumu koruyor",
                        "Kripto piyasasında {name} öne çıkıyor"
                ),
                List.of(
                        "{name}, son 24 saatte yüzde {change} değer kazanarak kripto para piyasasında öne çıkan varlıklar arasına girdi.",
                        "{name} fiyatı güçlü alım baskısıyla yüzde {change} yükseldi. Analistler momentum'un sürdürülebilirliğini yakından izliyor.",
                        "Kripto piyasasında {symbol} üzerindeki alım ilgisi artıyor; varlık son işlemlerde yüzde {change} değer kazandı.",
                        "{name} yüzde {change} artışla seans liderlerinden biri konumuna geldi. Yatırımcı tabanındaki genişleme ilgiyi canlı tutuyor."
                )
        ));

        m.put(SystemNewsScenario.EXTREME_RISE, new Templates(
                List.of(
                        "{name} sert yükselişiyle gündemin merkezinde",
                        "{name}'de çarpıcı alım dalgası",
                        "Yatırımcılar {name}'e yöneldi: Yüzde {change} artış kayıtlara geçti",
                        "{name} kripto piyasasının konuşulan ismi oldu"
                ),
                List.of(
                        "{name}, son 24 saatte yüzde {change}'i aşan keskin bir yükselişle tüm kripto para piyasasında dikkat çeken varlık konumuna geldi.",
                        "Kripto piyasasının gündemindeki isim {name} oldu. Yüzde {change} değer kazanan varlıkta işlem hacmi de belirgin biçimde artış gösterdi.",
                        "{name} yüzde {change}'lik yükselişiyle kripto varlıklar arasında en güçlü performansı sergileyen isimlerden biri oldu. Teknik göstergeler aşırı alım bölgesini işaret ediyor.",
                        "{name} fiyatı yüzde {change} artışla çarpıcı bir sıçrama gerçekleştirdi. Piyasa katılımcıları bu hareketin arkasındaki dinamikleri tartışıyor."
                )
        ));

        m.put(SystemNewsScenario.SHARP_FALL, new Templates(
                List.of(
                        "{name}'de sert satış baskısı gözlemleniyor",
                        "{name} fiyatı baskı altında",
                        "Kripto piyasasında {name} geriliyor",
                        "{name} değer kaybında: Yatırımcılar temkinli"
                ),
                List.of(
                        "{name}, son 24 saatte yüzde {change} değer kaybederek sert satış baskısıyla karşı karşıya kaldı. Piyasa katılımcıları gelişmeleri yakından takip ediyor.",
                        "Kripto para {name}, yüzde {change} gerilemeyle günün olumsuz yönde öne çıkan varlıkları arasında yer aldı.",
                        "{name} fiyatı yüzde {change} değer kaybetti. Kısa vadeli teknik görünüm baskılı seyrini sürdürüyor.",
                        "{symbol}'da belirginleşen satış baskısı fiyatı yüzde {change} aşağı çekti. Destek seviyeleri mercek altında."
                )
        ));

        m.put(SystemNewsScenario.EXTREME_FALL, new Templates(
                List.of(
                        "{name}'de derin gerileme piyasayı sarsıyor",
                        "{name} sert çöküşle gündemde",
                        "{name}'de yüksek kayıp yatırımcıları endişelendiriyor",
                        "Kripto piyasasında {name} sert düzeltme yaşıyor"
                ),
                List.of(
                        "{name} son 24 saatte yüzde {change} değer kaybıyla kripto para piyasasında sert bir düzeltme yaşadı. Uzmanlar kısa vadeli görünümü tartışıyor.",
                        "Kripto piyasasında {name} yüzde {change} değer kaybederek dikkat çekici bir düşüş sergiledi. Piyasa derinliği ve likidite mercek altında.",
                        "{name}'deki sert satış dalgası fiyatı yüzde {change} aşağı taşıdı. Teknik destek bölgelerindeki seyir belirleyici olacak.",
                        "{symbol} yüzde {change}'lik değer kaybıyla son dönemin en sert düzeltmelerinden birini gerçekleştirdi. Yatırımcılar pozisyonlarını yeniden değerlendiriyor."
                )
        ));

        return m;
    }

    private static Map<SystemNewsScenario, Templates> buildStockTemplates() {
        Map<SystemNewsScenario, Templates> m = new EnumMap<>(SystemNewsScenario.class);

        m.put(SystemNewsScenario.STRONG_RISE, new Templates(
                List.of(
                        "{name} hisselerinde güçlü yükseliş sürüyor",
                        "{name} seansta öne çıkan hisseler arasında",
                        "{name} hisseleri ivme kazanıyor",
                        "{name} borsanın gündemine taşındı"
                ),
                List.of(
                        "{name} hisseleri, bugünkü seansta yüzde {change} yükselerek Borsa İstanbul'da dikkat çeken isimler arasına girdi. Kurumsal yatırımcıların ilgisi artmaya devam ediyor.",
                        "Borsa İstanbul'da {name} hisseleri yüzde {change} artışla öne çıktı. Alım yönlü pozisyon alan yatırımcı tabanının genişlediği gözlemleniyor.",
                        "{name} hisseleri yüzde {change} değer kazanarak seans içinde güçlü bir seyir sergiledi. Teknik göstergeler yukarı yönlü baskının sürdüğüne işaret ediyor.",
                        "{symbol} hisseleri yüzde {change} artışıyla günün kazandıranları arasında yer aldı. Analistler orta vadeli görünümü olumlu değerlendiriyor."
                )
        ));

        m.put(SystemNewsScenario.EXTREME_RISE, new Templates(
                List.of(
                        "{name} hisselerinde çarpıcı yükseliş",
                        "{name} borsanın tartışmasız yıldızı",
                        "{name} hisseleri rekor yenileme yolunda",
                        "Yatırımcılar {name}'e akın etti: Yüzde {change} artış"
                ),
                List.of(
                        "{name} hisseleri seansta yüzde {change} yükselişle Borsa İstanbul'un en güçlü performansını sergileyen kağıtlar arasına girdi. Hacim verileri güçlü katılımın altını çiziyor.",
                        "{name} hisselerindeki yüzde {change}'lik sıçrama piyasanın gündemine damga vurdu. Uzmanlar hareketin temel nedenlerini mercek altına alıyor.",
                        "Borsa İstanbul'da {symbol} hisseleri yüzde {change} artışıyla çarpıcı bir yükseliş kaydetti. Fiyat, kritik teknik seviyeleri aşarak yeni bir fiyatlama sürecine girdi.",
                        "{name} hisselerindeki sert yükseliş piyasada geniş yankı uyandırdı. Yatırımcılar yüzde {change}'lik bu hareketi değerlendiriyor."
                )
        ));

        m.put(SystemNewsScenario.SHARP_FALL, new Templates(
                List.of(
                        "{name} hisselerinde belirgin satış baskısı",
                        "{name} seans içinde sert geriledi",
                        "{name} hisselerinde yoğun satış gözlemleniyor",
                        "{name} hisseleri baskı altında"
                ),
                List.of(
                        "{name} hisseleri, günlük yüzde {change} değer kaybıyla seans içinde en çok gerileyen kağıtlar arasına girdi. Analistler teknik görünümü değerlendiriyor.",
                        "{name}'de oluşan satış baskısı derinleşti. Hisseler yüzde {change} değer kaybıyla işlem görüyor; yatırımcılar destek seviyelerini yakından takip ediyor.",
                        "{symbol} hisseleri yüzde {change} gerileyerek seans içinde dikkat çekici bir hareket sergiledi. Kısa vadeli görünüm temkinli bir seyir izliyor.",
                        "{name} hisselerindeki yüzde {change}'lik değer kaybı piyasada öne çıkan gelişmeler arasında yer aldı. Temel verilerin yeniden fiyatlanıp fiyatlanmadığı tartışılıyor."
                )
        ));

        m.put(SystemNewsScenario.EXTREME_FALL, new Templates(
                List.of(
                        "{name} hisselerinde sert çöküş",
                        "{name} borsanın zor günü geçiren hisseleri arasında",
                        "{name} hisselerinde derin değer kaybı",
                        "{name}'de şiddetli satış dalgası"
                ),
                List.of(
                        "{name} hisseleri seansta yüzde {change} değer kaybıyla Borsa İstanbul'un en sert düzeltme yaşayan kağıtları arasına girdi. Piyasa uzmanları gelişmeyi yakından izliyor.",
                        "{name}'deki sert satış dalgası hisselerin değerini yüzde {change} aşağı çekti. Yatırımcılar stop-loss seviyelerini ve pozisyon yönetimini gözden geçiriyor.",
                        "Borsa İstanbul'da {symbol} hisseleri yüzde {change}'lik sert düşüşüyle günün en çok dikkat çeken kağıdı konumuna geldi. Sonraki seanslarda izlenecek seviyeler önem taşıyor.",
                        "{name} hisselerindeki yüzde {change}'lik kayıp son dönemin dikkat çekici hareketleri arasında yer alıyor. Temel ve teknik faktörler birlikte değerlendiriliyor."
                )
        ));

        return m;
    }

    private static Map<SystemNewsScenario, Templates> buildFxTemplates() {
        Map<SystemNewsScenario, Templates> m = new EnumMap<>(SystemNewsScenario.class);

        m.put(SystemNewsScenario.STRONG_RISE, new Templates(
                List.of(
                        "{name}'de dikkat çeken değer artışı",
                        "{name} kuru hareketlendi",
                        "{name} günün en çok değer kazanan dövizleri arasında",
                        "Döviz piyasasında {name} öne çıkıyor"
                ),
                List.of(
                        "{name} kuru, günlük bazda yüzde {change} artışla dikkat çekiyor. Döviz piyasalarındaki volatilite artış eğiliminde.",
                        "{name}, son işlemlerde yüzde {change} değer kazanarak döviz piyasasında öne çıktı. Merkez bankası politikaları ve küresel risk iştahı belirleyici olmaya devam ediyor.",
                        "Döviz piyasasında {name} yüzde {change} yükselerek günün dikkat çekici hareketi oldu. Makroekonomik veriler ve jeopolitik gelişmeler kurun seyrini etkiliyor.",
                        "{name} kurundaki yüzde {change}'lik artış piyasanın gündemine girdi. Yatırımcılar döviz sepetindeki dengelerin değişimini değerlendiriyor."
                )
        ));

        m.put(SystemNewsScenario.EXTREME_RISE, new Templates(
                List.of(
                        "{name}'de çarpıcı yükseliş döviz piyasasını hareketlendirdi",
                        "{name} kurunda sert sıçrama",
                        "Döviz piyasasında {name} alarmı: Yüzde {change} artış",
                        "{name} kuru keskin yükselişiyle gündemin zirvesine oturdu"
                ),
                List.of(
                        "{name} kuru yüzde {change}'lik sert yükselişiyle döviz piyasasında alarm zili çaldırdı. Merkez bankalarının olası adımları mercek altında.",
                        "{name}'deki yüzde {change}'lik ani değer artışı piyasalarda dalgalanmayı beraberinde getirdi. Uzmanlar hareketin kalıcılığını sorguluyor.",
                        "Döviz piyasasında {name} yüzde {change} yükselişiyle çarpıcı bir sıçrama kaydetti. Küresel sermaye akışlarındaki değişim ve jeopolitik riskler belirleyici faktörler arasında.",
                        "{name} kurundaki yüzde {change}'lik keskin artış piyasaları sarstı. Yatırımcılar döviz pozisyonlarını ve korunma stratejilerini yeniden değerlendiriyor."
                )
        ));

        m.put(SystemNewsScenario.SHARP_FALL, new Templates(
                List.of(
                        "{name}'de gerileme sürüyor",
                        "{name} kuru baskı altında",
                        "{name} değer kaybediyor",
                        "Döviz piyasasında {name} satış baskısıyla karşı karşıya"
                ),
                List.of(
                        "{name} kuru günlük bazda yüzde {change} değer kaybıyla dikkat çekici bir seyir izliyor. Döviz piyasası yakından takip ediliyor.",
                        "{name}, son işlemlerde yüzde {change} değer kaybetti. Küresel piyasalardaki risk algısındaki değişim döviz kurlarını etkiliyor.",
                        "Döviz piyasasında {name} yüzde {change} düşüşüyle günün öne çıkan hareketi oldu. Ekonomik veriler ve jeopolitik gelişmeler kurun seyrinde belirleyici rol üstleniyor.",
                        "{name} kurundaki yüzde {change}'lik gerileme, yatırımcıların döviz stratejilerini sorgulamasına yol açtı. Teknik destek seviyeleri izlenmeye devam ediyor."
                )
        ));

        m.put(SystemNewsScenario.EXTREME_FALL, new Templates(
                List.of(
                        "{name}'de sert değer kaybı döviz piyasasını sarstı",
                        "{name} kurunda derin gerileme",
                        "Döviz piyasasında {name} alarmı: Yüzde {change} kayıp",
                        "{name} keskin düşüşüyle gündemde"
                ),
                List.of(
                        "{name} kuru yüzde {change}'lik sert düşüşüyle döviz piyasasında belirgin dalgalanmaya yol açtı. Politika tepkileri yakından takip ediliyor.",
                        "{name}'deki yüzde {change}'lik ani değer kaybı piyasalarda tedirginliği artırdı. Merkez bankalarının tutumu ve küresel risk iştahı belirleyici faktörler arasında.",
                        "Döviz piyasasında {name} yüzde {change} düşüşüyle çarpıcı bir değer kaybı yaşadı. Yatırımcılar korunma araçlarına yönelirken teknik destek seviyeleri kritik önem taşıyor.",
                        "{name} kurundaki yüzde {change}'lik keskin düşüş piyasaları hareketlendirdi. Uzmanlar oluşan yeni denge noktasını değerlendiriyor."
                )
        ));

        return m;
    }

    private static Map<SystemNewsScenario, Templates> buildCommodityTemplates() {
        Map<SystemNewsScenario, Templates> m = new EnumMap<>(SystemNewsScenario.class);

        m.put(SystemNewsScenario.STRONG_RISE, new Templates(
                List.of(
                        "{name} fiyatlarında belirgin yükseliş",
                        "{name} piyasasında güçlü talep baskısı",
                        "{name} fiyatı ivme kazanıyor",
                        "Emtia piyasasında {name} öne çıkıyor"
                ),
                List.of(
                        "{name} fiyatları günlük bazda yüzde {change} artışla emtia piyasasında öne çıktı. Arz-talep dengesi ve küresel piyasa koşulları yatırımcıların odağında.",
                        "{name} yüzde {change} yükselişiyle emtia piyasasının dikkat çeken ismi oldu. Küresel talep dinamikleri ve jeopolitik gelişmeler fiyatlamayı etkiliyor.",
                        "Emtia piyasasında {name} fiyatı yüzde {change} artışla güçlü bir seyir izliyor. Enflasyon beklentileri ve dolar endeksi belirleyici rol oynuyor.",
                        "{name} fiyatlarındaki yüzde {change}'lik yükseliş emtia piyasasının gündemine taşındı. Üretim verileri ve stok seviyeleri yakından izleniyor."
                )
        ));

        m.put(SystemNewsScenario.EXTREME_RISE, new Templates(
                List.of(
                        "{name} fiyatlarında çarpıcı sıçrama",
                        "{name} piyasasında arz endişesi fiyatları yukarı taşıdı",
                        "Emtia piyasasında {name} yükseliş rüzgarı",
                        "{name} keskin yükselişiyle küresel piyasaları hareketlendirdi"
                ),
                List.of(
                        "{name} fiyatları yüzde {change}'lik sert yükselişiyle emtia piyasasında gündemin merkezine oturdu. Arz kısıtlamaları ve artan küresel talep baskı oluşturuyor.",
                        "{name} piyasasında yüzde {change}'lik çarpıcı artış görüldü. Jeopolitik riskler ve stok düşüklüğü fiyat hareketinin temel dinamikleri arasında gösteriliyor.",
                        "Emtia piyasasında {name} yüzde {change} artışla tüm dikkatleri üzerine çekti. Uzmanlar bu hareketin sürdürülebilirliğini ve küresel ekonomiye etkilerini tartışıyor.",
                        "{name} fiyatlarındaki yüzde {change}'lik sıçrama piyasalarda geniş yankı uyandırdı. Talep tarafındaki yapısal değişimler ve spekülatif pozisyonlar değerlendiriliyor."
                )
        ));

        m.put(SystemNewsScenario.SHARP_FALL, new Templates(
                List.of(
                        "{name} fiyatlarında sert düzeltme",
                        "{name} piyasasında baskı hâkim",
                        "{name} fiyatı gerilemeye devam ediyor",
                        "Emtia piyasasında {name} satış baskısıyla karşı karşıya"
                ),
                List.of(
                        "{name} fiyatları günlük yüzde {change} değer kaybıyla baskılı seyretti. Küresel emtia piyasalarındaki gelişmeler yakından izleniyor.",
                        "{name} piyasasında yüzde {change}'lik gerileme dikkat çekti. Küresel talep yavaşlaması ve artan stok seviyeleri baskıyı pekiştiriyor.",
                        "Emtia piyasasında {name} fiyatı yüzde {change} düşüşüyle günün öne çıkan hareketi oldu. Ekonomik büyüme beklentileri ve dolar endeksindeki seyir belirleyici.",
                        "{name} fiyatlarındaki yüzde {change}'lik gerileme emtia piyasasının gündemine girdi. Arz bolluğu ve zayıflayan talep baskının temel nedenleri arasında yer alıyor."
                )
        ));

        m.put(SystemNewsScenario.EXTREME_FALL, new Templates(
                List.of(
                        "{name} fiyatlarında derin çöküş",
                        "{name} piyasasında sert satış dalgası",
                        "Emtia piyasasında {name} sert değer kaybetti",
                        "{name} fiyatları çöküşte: Piyasa alarm veriyor"
                ),
                List.of(
                        "{name} fiyatları yüzde {change}'lik derin gerilemeyle emtia piyasasında sert bir düzeltme yaşandığına işaret ediyor. Küresel talep zayıflığı ve arz baskısı belirleyici etkenler arasında.",
                        "{name} piyasasında yüzde {change}'lik sert düşüş küresel emtia piyasalarında kaygı yarattı. Uzmanlar oluşan yeni fiyat dengesini değerlendiriyor.",
                        "Emtia piyasasında {name} yüzde {change} değer kaybıyla çarpıcı bir düzeltme yaşadı. Konjonktürel veriler ve piyasa dinamikleri yakından takip ediliyor.",
                        "{name} fiyatlarındaki yüzde {change}'lik ani çöküş piyasaları sarstı. Spekülatif pozisyonların tasfiyesi ve teknik bozulmalar düşüşün hız kazanmasına zemin hazırladı."
                )
        ));

        return m;
    }

    private static Map<SystemNewsScenario, Templates> buildFundTemplates() {
        Map<SystemNewsScenario, Templates> m = new EnumMap<>(SystemNewsScenario.class);

        m.put(SystemNewsScenario.STRONG_RISE, new Templates(
                List.of(
                        "{name} fonu yatırımcısına güçlü getiri sunuyor",
                        "{name} fon birim pay değerinde kayda değer artış",
                        "{name} fonu performansıyla öne çıkıyor",
                        "TEFAS'ta {name} fonu dikkat çekiyor"
                ),
                List.of(
                        "{name} fonu, yüzde {change} artışla birim pay değerini yükseltirken fon performansı dikkat çekici bir seyir izliyor. Portföyün varlık dağılımı bu başarıda belirleyici rol oynuyor.",
                        "{name} fonunun birim pay değeri yüzde {change} artışla öne çıkan fonlar arasına girdi. Yatırımcılar fon stratejisini ve portföy kompozisyonunu yakından takip ediyor.",
                        "TEFAS piyasasında {name} fonu yüzde {change} artışıyla güçlü bir performans sergiledi. Portföyündeki varlıkların değer kazanması fon getirisine olumlu yansıdı.",
                        "{name} fonunda yüzde {change}'lik birim pay değeri artışı yatırımcıların dikkatini çekiyor. Fonun yatırım stratejisi ve portföy yönetimi değerlendiriliyor."
                )
        ));

        m.put(SystemNewsScenario.EXTREME_RISE, new Templates(
                List.of(
                        "{name} fonunda çarpıcı performans",
                        "{name} fonu olağandışı getiriyle gündemde",
                        "TEFAS'ta {name} fonu yüzde {change} yükselişiyle dikkat çekiyor",
                        "{name} fonu yatırımcısını şaşırttı"
                ),
                List.of(
                        "{name} fonu yüzde {change}'lik olağandışı artışla TEFAS piyasasının gündemine girdi. Portföyün yapısı ve yönetim stratejisi bu çarpıcı performansın arkasındaki dinamikler arasında yer alıyor.",
                        "{name} fonunun birim pay değeri yüzde {change} artışıyla dikkat çeken fonlar arasına girdi. Bu performansın sürdürülebilirliği yatırımcılar tarafından sorgulanıyor.",
                        "TEFAS'ta {name} fonu yüzde {change}'lik yükselişiyle günün en güçlü getirilerinden birini kaydetti. Fon stratejisi ve portföy yönetimi yakından izleniyor.",
                        "{name} fonunda görülen yüzde {change}'lik sıçrama piyasada geniş yankı uyandırdı. Portföydeki varlık seçimi ve yönetim başarısı tartışılıyor."
                )
        ));

        m.put(SystemNewsScenario.SHARP_FALL, new Templates(
                List.of(
                        "{name} fonunda birim pay değeri geriledi",
                        "{name} fonu baskılı seyir izliyor",
                        "{name} fonunda değer kaybı dikkat çekiyor",
                        "TEFAS'ta {name} fonu değer kaybetti"
                ),
                List.of(
                        "{name} fonunun birim pay değeri yüzde {change} geriledi. Yatırımcılar fon portföyündeki varlık dağılımını ve yönetim stratejisini değerlendiriyor.",
                        "{name} fonu yüzde {change} değer kaybıyla baskılı bir seyir izledi. Portföydeki piyasa koşulları ve varlık performansı bu harekette etkili oldu.",
                        "TEFAS piyasasında {name} fonu yüzde {change} düşüşüyle dikkat çekti. Yatırımcılar fon stratejisini ve kısa vadeli beklentilerini yeniden değerlendiriyor.",
                        "{name} fonundaki yüzde {change}'lik gerileme yatırımcıların gündemine girdi. Fonun portföy yapısı ve piyasa koşullarına uyum kapasitesi mercek altında."
                )
        ));

        m.put(SystemNewsScenario.EXTREME_FALL, new Templates(
                List.of(
                        "{name} fonunda derin değer kaybı",
                        "{name} fonu sert düzeltmeyle gündemde",
                        "TEFAS'ta {name} fonu sert geriledi",
                        "{name} fonundaki kayıp yatırımcıları endişelendiriyor"
                ),
                List.of(
                        "{name} fonu yüzde {change}'lik sert değer kaybıyla TEFAS piyasasında dikkat çeken fonlar arasına girdi. Portföydeki yatırım araçlarının performansı bu düşüşte belirleyici oldu.",
                        "{name} fonunun birim pay değeri yüzde {change} gerileyerek dikkat çekici bir düzeltme yaşadı. Yatırımcılar fon yönetiminin olası tutumunu değerlendiriyor.",
                        "TEFAS piyasasında {name} fonu yüzde {change}'lik sert düşüşüyle günün öne çıkan gelişmesi oldu. Fonun portföy stratejisi ve risk yönetimi sorgulanıyor.",
                        "{name} fondaki yüzde {change}'lik derin gerileme yatırımcılar arasında kaygı yarattı. Fon kompozisyonu ve kısa vadeli strateji değişikliği ihtimali gündemde yer alıyor."
                )
        ));

        return m;
    }
}
