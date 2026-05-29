package com.emrehalli.financeportal.common.logging;

public final class LoggingConstants {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_KEY = "requestId";
    public static final String LOG_TYPE_KEY = "log_type";
    public static final String USER_ID_KEY = "userId";
    public static final String USERNAME_KEY = "username";
    public static final String ROLE_KEY = "role";
    public static final String ACTION_KEY = "action";
    public static final String RESOURCE_TYPE_KEY = "resourceType";
    public static final String RESOURCE_ID_KEY = "resourceId";
    public static final String IP_ADDRESS_KEY = "ipAddress";
    public static final String USER_AGENT_KEY = "userAgent";
    public static final String METHOD_KEY = "method";
    public static final String URI_KEY = "uri";
    public static final String QUERY_STRING_KEY = "queryString";
    public static final String STATUS_KEY = "status";
    public static final String STATUS_CODE_KEY = "statusCode";
    public static final String DURATION_MS_KEY = "durationMs";
    public static final String PATH_KEY = "path";
    public static final String PROVIDER_KEY = "provider";
    public static final String PROVIDER_NAME_KEY = "providerName";
    public static final String ENDPOINT_KEY = "endpoint";
    public static final String RESPONSE_TIME_MS_KEY = "responseTimeMs";
    public static final String RETRY_COUNT_KEY = "retryCount";
    public static final String SCHEDULER_NAME_KEY = "schedulerName";
    public static final String STARTED_AT_KEY = "startedAt";
    public static final String FINISHED_AT_KEY = "finishedAt";
    public static final String PROCESSED_COUNT_KEY = "processedCount";
    public static final String SUCCESS_COUNT_KEY = "successCount";
    public static final String FAILED_COUNT_KEY = "failedCount";
    public static final String EXCEPTION_TYPE_KEY = "exceptionType";
    public static final String SOURCE_KEY = "source";
    public static final String SUCCESS_KEY = "success";
    public static final String FETCHED_ITEM_COUNT_KEY = "fetchedItemCount";
    public static final String REQUEST_START_TIME_ATTR = LoggingConstants.class.getName() + ".requestStartTime";
    public static final String REQUEST_ID_ATTR = LoggingConstants.class.getName() + ".requestId";

    private LoggingConstants() {
    }
}




