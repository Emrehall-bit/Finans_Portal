CREATE OR REPLACE FUNCTION fp_norm(t text) RETURNS text AS $$
  SELECT regexp_replace(
    lower(translate(
      coalesce(t,''),
      'IİışŞğĞüÜöÖçÇ',
      'iiissgguuroocc'
    )),
    '[^a-z0-9]+', ' ', 'g'
  );
$$ LANGUAGE sql IMMUTABLE;

CREATE OR REPLACE FUNCTION fp_kw(haystack text, needle text) RETURNS boolean AS $$
  SELECT (' ' || fp_norm(haystack) || ' ') LIKE ('% ' || fp_norm(needle) || ' %');
$$ LANGUAGE sql IMMUTABLE;
