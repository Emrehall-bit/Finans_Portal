import test from "node:test";
import assert from "node:assert/strict";
import {
  buildNewsPlaceholderLabel,
  formatNewsPublishedAt,
  getNewsFallbackLogoUrl,
  getNewsProviderLabel,
  getNewsSummaryText,
} from "./newsCardUtils.js";

test("maps provider enums to display labels", () => {
  assert.equal(getNewsProviderLabel("AA_RSS"), "Anadolu Ajans\u0131");
  assert.equal(getNewsProviderLabel("FINNHUB"), "Finnhub");
  assert.equal(getNewsProviderLabel("INVESTING_RSS"), "Investing.com");
});

test("returns AA initials for AA_RSS provider", () => {
  assert.equal(buildNewsPlaceholderLabel({ provider: "AA_RSS" }), "AA");
});

test("returns Investing.com initials for INVESTING_RSS provider", () => {
  assert.equal(buildNewsPlaceholderLabel({ provider: "INVESTING_RSS" }), "IN");
});

test("falls back to generic initials for unknown providers", () => {
  assert.equal(buildNewsPlaceholderLabel({ provider: "UNKNOWN_PROVIDER" }), "UP");
});

test("returns Turkish summary fallback when summary is missing", () => {
  assert.equal(getNewsSummaryText(""), "\u00d6zet bilgisi bulunmuyor.");
});

test("returns Turkish date fallback when publishedAt is missing", () => {
  assert.equal(formatNewsPublishedAt(null), "Tarih bilgisi al\u0131namad\u0131");
});

test("returns provider based fallback logo url", () => {
  assert.equal(
    getNewsFallbackLogoUrl({ provider: "AA_RSS" }),
    "https://www.google.com/s2/favicons?domain=aa.com.tr&sz=128",
  );
});

test("returns article domain fallback logo url when provider is unknown", () => {
  assert.equal(
    getNewsFallbackLogoUrl({ provider: "UNKNOWN", url: "https://example.com/news/item-1" }),
    "https://www.google.com/s2/favicons?domain=example.com&sz=128",
  );
});
