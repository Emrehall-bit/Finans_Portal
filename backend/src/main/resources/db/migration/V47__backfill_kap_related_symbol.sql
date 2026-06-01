UPDATE news
SET related_symbol = TRIM(UPPER(SPLIT_PART(title, ' - ', 1)))
WHERE is_kap_disclosure = true
  AND (related_symbol IS NULL OR related_symbol = '')
  AND title ~ '^[A-Z]{2,6} - ';
