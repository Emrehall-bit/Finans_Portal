-- ============================================================
-- BANKING: Son 50 haber + keyword hit analizi
-- ============================================================
WITH banking_news AS (
  SELECT id, title, summary, category,
         COALESCE(title,'') || ' ' || COALESCE(summary,'') AS combined
  FROM news
  WHERE COALESCE(is_kap_disclosure, false) = false
    AND category = 'BANKING'
  ORDER BY published_at DESC, created_at DESC
  LIMIT 50
),
kw_list(kw) AS (
  VALUES
    ('bankacilik'),('banka'),('bankasi'),('bankalari'),('bankaciligi'),
    ('garanti bankasi'),('is bankasi'),('halkbank'),('ziraat bankasi'),
    ('akbank'),('yapi kredi'),('vakifbank'),('finansbank'),
    ('mevduat'),('mevduati'),('kredi'),('krediye'),('kredide'),('kredisi'),
    ('loan'),('deposit'),
    ('net interest margin'),('net faiz marji'),
    ('bankacilik hisseleri'),('banka hisseleri'),('takipteki kredi')
),
-- Per-news per-keyword scores: title=4, summary=2, combined=1 (combined includes title+summary)
scores AS (
  SELECT
    n.id,
    n.title,
    k.kw,
    (CASE WHEN fp_kw(n.title,   k.kw) THEN 4 ELSE 0 END +
     CASE WHEN fp_kw(n.summary, k.kw) THEN 2 ELSE 0 END +
     CASE WHEN fp_kw(n.combined,k.kw) THEN 1 ELSE 0 END) AS pts
  FROM banking_news n CROSS JOIN kw_list k
),
hits AS (
  SELECT id, title, kw, pts
  FROM scores
  WHERE pts > 0
)
SELECT id, title, kw, pts
FROM hits
ORDER BY id DESC, pts DESC;
