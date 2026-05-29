package com.emrehalli.financeportal.market.persistence;

import java.time.Instant;

public interface LastHistoryDateProjection {
    String getCode();
    Instant getLastTimestamp();
}
