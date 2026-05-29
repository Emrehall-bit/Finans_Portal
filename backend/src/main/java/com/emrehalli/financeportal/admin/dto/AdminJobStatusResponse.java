package com.emrehalli.financeportal.admin.dto;

public record AdminJobStatusResponse(
        boolean running,
        int processed,
        int total
) {
}




