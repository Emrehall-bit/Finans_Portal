package com.emrehalli.financeportal.common.logging;

public final class LoggingConstants {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_KEY = "requestId";
    public static final String USER_ID_KEY = "userId";
    public static final String METHOD_KEY = "method";
    public static final String URI_KEY = "uri";
    public static final String QUERY_STRING_KEY = "queryString";
    public static final String STATUS_KEY = "status";
    public static final String DURATION_MS_KEY = "durationMs";
    public static final String PATH_KEY = "path";
    public static final String PROVIDER_NAME_KEY = "providerName";
    public static final String SOURCE_KEY = "source";
    public static final String SUCCESS_KEY = "success";
    public static final String FETCHED_ITEM_COUNT_KEY = "fetchedItemCount";
    public static final String REQUEST_START_TIME_ATTR = LoggingConstants.class.getName() + ".requestStartTime";
    public static final String REQUEST_ID_ATTR = LoggingConstants.class.getName() + ".requestId";

    private LoggingConstants() {
    }
}
