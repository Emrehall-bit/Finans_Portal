import test from "node:test";
import assert from "node:assert/strict";
import { buildNewsQueryParams } from "./newsPageQueryUtils.js";

test("builds default newest request without empty filters", () => {
  assert.deepEqual(
    buildNewsQueryParams({ keyword: "", category: "", provider: "", language: "" }, 0),
    {
      isKapDisclosure: false,
      page: 0,
      size: 12,
      sortBy: "publishedAt",
      sortDirection: "desc",
    }
  );
});

test("builds importance score request with active filters", () => {
  assert.deepEqual(
    buildNewsQueryParams(
      { keyword: "", category: "ECONOMY", provider: "AA_RSS", language: "tr" },
      0,
      "importanceScore"
    ),
    {
      category: "ECONOMY",
      provider: "AA_RSS",
      language: "tr",
      isKapDisclosure: false,
      page: 0,
      size: 12,
      sortBy: "importanceScore",
      sortDirection: "desc",
    }
  );
});

test("omits language when all languages are selected", () => {
  assert.deepEqual(
    buildNewsQueryParams({ keyword: "", category: "", provider: "", language: "" }, 1),
    {
      isKapDisclosure: false,
      page: 1,
      size: 12,
      sortBy: "publishedAt",
      sortDirection: "desc",
    }
  );
});

test("builds KAP feed request when feed type is kap", () => {
  assert.deepEqual(
    buildNewsQueryParams({ keyword: "", category: "", provider: "KAP", language: "" }, 0, "publishedAt", "kap"),
    {
      provider: "KAP",
      isKapDisclosure: true,
      page: 0,
      size: 12,
      sortBy: "publishedAt",
      sortDirection: "desc",
    },
  );
});
