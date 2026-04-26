import test from "node:test";
import assert from "node:assert/strict";
import { buildNewsQueryParams } from "./newsPageQueryUtils.js";

test("builds default newest request without empty filters", () => {
  assert.deepEqual(
    buildNewsQueryParams({ keyword: "", category: "", provider: "", language: "" }, 0),
    {
      page: 0,
      size: 20,
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
      page: 0,
      size: 20,
      sortBy: "importanceScore",
      sortDirection: "desc",
    }
  );
});
