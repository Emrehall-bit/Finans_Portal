package com.emrehalli.financeportal.market.api;

import com.emrehalli.financeportal.common.i18n.AppMessageSource;
import com.emrehalli.financeportal.common.response.ApiResponse;
import org.springframework.stereotype.Component;

@Component
public class MarketApiResponseFactory {

    private final AppMessageSource appMessageSource;

    public MarketApiResponseFactory(AppMessageSource appMessageSource) {
        this.appMessageSource = appMessageSource;
    }

    public <T> ApiResponse<T> success(T data, String messageKey, Object... args) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .message(appMessageSource.get(messageKey, args))
                .build();
    }
}
