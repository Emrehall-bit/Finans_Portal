package com.emrehalli.financeportal.market.service;

import java.util.List;

public record MacroSyncResult(List<String> indicatorCodes, int fetched, int saved, int duplicates, int skipped) {}




