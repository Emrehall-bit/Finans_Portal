-- Son 1000 KAP dışı haber: kategori dağılımı
WITH recent AS (
  SELECT id, title, summary, category
  FROM news
  WHERE COALESCE(is_kap_disclosure, false) = false
  ORDER BY published_at DESC, created_at DESC
  LIMIT 1000
)
SELECT category, COUNT(*) AS cnt
FROM recent
GROUP BY category
ORDER BY cnt DESC;
