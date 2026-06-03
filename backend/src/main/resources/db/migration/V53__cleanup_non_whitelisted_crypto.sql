-- Whitelist dışındaki kripto instrument kayıtlarını ve bağlı fiyat/history verilerini temizler.
-- Whitelist: CryptoWhitelist.java ile senkron tutulmalı.
--
-- Etkilenen tablolar:
--   market_price_history  → silinir
--   market_prices         → silinir
--   market_instruments    → silinir
--
-- Dokunulmayan tablolar:
--   chart_drawings, indicator_configs  → kayıt varsa EXCEPTION fırlatılır, işlem durur
--   watchlist, alerts, portfolio_holdings → string instrument_code kullanır, FK yok, dokunulmaz
--   fundamental_*, company_financials  → crypto kaydı bulunmaz, dokunulmaz

DO $$
DECLARE
    target_ids      BIGINT[];
    blocking_codes  TEXT;
BEGIN
    SELECT ARRAY_AGG(id)
    INTO target_ids
    FROM market_instruments
    WHERE instrument_type = 'CRYPTO'
      AND source_name     = 'BINANCE'
      AND instrument_code NOT IN (
          'BTC','ETH','USDT','BNB','XRP','SOL','USDC','TRX','DOGE','ADA',
          'HYPE','BCH','LINK','LEO','XLM','AVAX','TON','SUI','SHIB','LTC',
          'DOT','HBAR','UNI','DAI','PEPE','PI','AAVE','NEAR','APT','ETC',
          'ONDO','ICP','OKB','CRO','ATOM','KAS','POL','ARB','VET','ALGO',
          'FIL','RENDER','FET','WLD','SEI','OP','INJ','QNT','XMR','STX',
          'TIA','IMX','MKR','GRT','THETA','JUP','LDO','JASMY','RUNE','PYTH',
          'FLOW','SAND','GALA','MANA','AXS','CHZ','APE','ENA','PENDLE','CRV',
          'COMP','SNX','DYDX','1INCH','ZEC','DASH','EGLD','KAVA','MINA','ROSE',
          'CELO','GMT','ZIL','BAT','QTUM','IOTA','NEO','KSM','LRC','ENS',
          'MASK','API3','BLUR','ID','ARKM','ALT','STRK','AEVO','MEME','NOT',
          'WIF','BONK','FLOKI','PENGU','TURBO','PNUT','ORDI','SATS','BOME','PEOPLE',
          'TWT','CAKE','RAY','JTO','WOO','ZRX','SUSHI','YFI','GMX','CVX',
          'BAL','UMA','BAND','SKL','ANKR','COTI','CTSI','OCEAN','LPT','AUDIO',
          'ARPA','IOST','ONE','HIVE','XTZ','EOS','XEC','CFX','FTM','LUNC',
          'LUNA','KDA','DCR','SC','RVN','GLM','ELF','TFUEL','FIDA','MAGIC'
      );

    IF target_ids IS NULL OR ARRAY_LENGTH(target_ids, 1) = 0 THEN
        RAISE NOTICE '[V53] Whitelist disi crypto instrument bulunamadi, islem yok.';
        RETURN;
    END IF;

    RAISE NOTICE '[V53] % adet whitelist disi crypto instrument temizlenecek.', ARRAY_LENGTH(target_ids, 1);

    -- chart_drawings veya indicator_configs'te referans varsa dur, kullanıcı elle karar versin.
    SELECT STRING_AGG(DISTINCT mi.instrument_code, ', ' ORDER BY mi.instrument_code)
    INTO blocking_codes
    FROM market_instruments mi
    WHERE mi.id = ANY(target_ids)
      AND (
          EXISTS (SELECT 1 FROM chart_drawings    cd WHERE cd.instrument_id = mi.id)
       OR EXISTS (SELECT 1 FROM indicator_configs ic WHERE ic.instrument_id = mi.id)
      );

    IF blocking_codes IS NOT NULL THEN
        RAISE EXCEPTION
            '[V53] Kullanici verisi bulundugu icin silme durduruldu. '
            'chart_drawings veya indicator_configs icinde referans bulunan instrumentlar: [%]. '
            'Bu kayitlari elle inceleyin, gerekiyorsa silin, sonra migration''i tekrar calistirin.',
            blocking_codes;
    END IF;

    -- Güvenli silme sırası (FK sırasına göre)
    DELETE FROM market_price_history WHERE instrument_id = ANY(target_ids);
    DELETE FROM market_prices        WHERE instrument_id = ANY(target_ids);
    DELETE FROM market_instruments   WHERE id            = ANY(target_ids);

    RAISE NOTICE '[V53] Temizlik tamamlandi.';
END $$;
