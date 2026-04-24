import test from "node:test";
import assert from "node:assert/strict";
import {
  buildNewsPlaceholderLabel,
  formatNewsPublishedAt,
  getNewsProviderLabel,
  getNewsSummaryText,
} from "./newsCardUtils.js";

test("maps provider enums to display labels", () => {
  assert.equal(getNewsProviderLabel("BLOOMBERG_HT"), "Bloomberg HT");
  assert.equal(getNewsProviderLabel("AA_RSS"), "Anadolu Ajansı");
  assert.equal(getNewsProviderLabel("FINNHUB"), "Finnhub");
});

test("returns AA initials for AA_RSS provider", () => {
  assert.equal(buildNewsPlaceholderLabel({ provider: "AA_RSS" }), "AA");
});

test("falls back to generic initials for other providers", () => {
  assert.equal(buildNewsPlaceholderLabel({ provider: "BLOOMBERG_HT" }), "BH");
});

test("returns Turkish summary fallback when summary is missing", () => {
  assert.equal(getNewsSummaryText(""), "Özet bilgisi bulunmuyor.");
});

test("returns Turkish date fallback when publishedAt is missing", () => {
  assert.equal(formatNewsPublishedAt(null), "Tarih bilgisi alınamadı");
});
