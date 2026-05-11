package com.emrehalli.financeportal.market.api;

public record AdminJobStatusResponse(
        boolean running,
        int processed,
        int total
) {
}
