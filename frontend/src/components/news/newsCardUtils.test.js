import test from "node:test";
import assert from "node:assert/strict";
import {
  buildNewsPlaceholderLabel,
  formatNewsPublishedAt,
  getNewsFallbackLogoUrl,
  getNewsPrimaryActionLabel,
  getNewsProviderLabel,
  getNewsSourceUrl,
  getNewsSummaryText,
  isKapDisclosure,
  shouldShowSourceCta,
} from "./newsCardUtils.js";

test("maps provider enums to display labels", () => {
  assert.equal(getNewsProviderLabel("AA_RSS"), "Anadolu Ajansi");
  assert.equal(getNewsProviderLabel("CNBC_RSS"), "CNBC");
  assert.equal(getNewsProviderLabel("KAP"), "KAP");
});

test("returns provider initials for known providers", () => {
  assert.equal(buildNewsPlaceholderLabel({ provider: "AA_RSS" }), "AA");
  assert.equal(buildNewsPlaceholderLabel({ provider: "CNBC_RSS" }), "CNBC");
});

test("falls back to generic initials for unknown providers", () => {
  assert.equal(buildNewsPlaceholderLabel({ provider: "UNKNOWN_PROVIDER" }), "UP");
});

test("returns summary fallback when summary is missing", () => {
  assert.equal(getNewsSummaryText(""), "Ozet bilgisi bulunmuyor.");
});

test("returns date fallback when publishedAt is missing", () => {
  assert.equal(formatNewsPublishedAt(null), "Tarih bilgisi alinamadi");
});

test("uses publishedAt and parses timezone-less backend timestamps as UTC", () => {
  assert.equal(
    formatNewsPublishedAt({ publishedAt: "2026-05-24T16:04:57Z" }),
    formatNewsPublishedAt({ publishedAt: "2026-05-24T16:04:57" }),
  );
});

test("falls back to createdAt when publishedAt is missing", () => {
  assert.equal(
    formatNewsPublishedAt({ createdAt: "2026-05-24 16:04:57" }),
    formatNewsPublishedAt({ publishedAt: "2026-05-24T16:04:57Z" }),
  );
});

test("returns provider based fallback logo url", () => {
  assert.equal(
    getNewsFallbackLogoUrl({ provider: "CNBC_RSS" }),
    "https://www.google.com/s2/favicons?domain=cnbc.com&sz=128",
  );
});

test("returns article domain fallback logo url when provider is unknown", () => {
  assert.equal(
    getNewsFallbackLogoUrl({ provider: "UNKNOWN", url: "https://example.com/news/item-1" }),
    "https://www.google.com/s2/favicons?domain=example.com&sz=128",
  );
});

test("detects KAP disclosure records", () => {
  assert.equal(isKapDisclosure({ provider: "KAP" }), true);
  assert.equal(isKapDisclosure({ isKapDisclosure: true }), true);
});

test("uses normalized source url when present", () => {
  assert.equal(
    getNewsSourceUrl({ sourceUrl: "https://kap.org.tr/tr/Bildirim/123", url: "https://fallback.example" }),
    "https://kap.org.tr/tr/Bildirim/123",
  );
});

test("shows source CTA for source-link-only, summary-only and KAP items", () => {
  assert.equal(shouldShowSourceCta({ qualityStatus: "SOURCE_LINK_ONLY" }), true);
  assert.equal(shouldShowSourceCta({ qualityStatus: "SUMMARY_ONLY", provider: "AA_RSS" }), true);
  assert.equal(shouldShowSourceCta({ provider: "KAP" }), true);
});

test("returns source action label when source CTA is required", () => {
  assert.equal(getNewsPrimaryActionLabel({ provider: "KAP" }, "Haberi ac"), "Kaynakta oku");
});
