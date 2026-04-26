package com.emrehalli.financeportal.common.logging;

import jakarta.servlet.FilterChain;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityFiltersTest {

    @AfterEach
    void clearMdc() {
        LoggingContext.clear();
    }

    @Test
    void requestCorrelationFilterUsesIncomingRequestIdAndExposesItBeforeChain() throws Exception {
        RequestCorrelationFilter filter = new RequestCorrelationFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/news");
        request.addHeader(LoggingConstants.REQUEST_ID_HEADER, "abc123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> requestIdInChain = new AtomicReference<>();

        FilterChain chain = (req, res) -> {
            requestIdInChain.set(LoggingContext.get(LoggingConstants.REQUEST_ID_KEY));
            assertThat(((MockHttpServletRequest) req).getAttribute(LoggingConstants.REQUEST_ID_ATTR)).isEqualTo("abc123");
            assertThat(((MockHttpServletResponse) res).getHeader(LoggingConstants.REQUEST_ID_HEADER)).isEqualTo("abc123");
        };

        filter.doFilter(request, response, chain);

        assertThat(requestIdInChain.get()).isEqualTo("abc123");
        assertThat(request.getAttribute(LoggingConstants.REQUEST_ID_ATTR)).isEqualTo("abc123");
        assertThat(response.getHeader(LoggingConstants.REQUEST_ID_HEADER)).isEqualTo("abc123");
        assertThat(LoggingContext.get(LoggingConstants.REQUEST_ID_KEY)).isNull();
    }

    @Test
    void httpRequestLoggingFilterLogsRequestFieldsFromRequestAndResponse() throws Exception {
        HttpRequestLoggingFilter filter = new HttpRequestLoggingFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/news");
        request.setQueryString("page=1");
        request.setAttribute("requestId", "abc123");
        request.setAttribute(LoggingConstants.REQUEST_ID_ATTR, "abc123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<LogEvent> capturedEvent = new AtomicReference<>();
        TestAppender appender = new TestAppender("http-request-test", capturedEvent);
        Logger logger = (Logger) LogManager.getLogger(HttpRequestLoggingFilter.class);
        Level previousLevel = logger.getLevel();

        logger.addAppender(appender);
        logger.setAdditive(false);
        logger.setLevel(Level.INFO);
        appender.start();

        try {
            FilterChain chain = (req, res) -> {
                ((jakarta.servlet.http.HttpServletResponse) res).setStatus(200);
                res.getWriter().write("{\"ok\":true}");
            };

            filter.doFilter(request, response, chain);
        } finally {
            logger.removeAppender((Appender) appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }

        LogEvent event = capturedEvent.get();
        assertThat(event).isNotNull();
        Map<String, String> contextData = event.getContextData().toMap();
        assertThat(event.getMessage().getFormattedMessage()).isEqualTo("HTTP request completed");
        assertThat(contextData.get(LoggingConstants.REQUEST_ID_KEY)).isEqualTo("abc123");
        assertThat(contextData.get(LoggingConstants.METHOD_KEY)).isEqualTo("GET");
        assertThat(contextData.get(LoggingConstants.URI_KEY)).isEqualTo("/api/v1/news");
        assertThat(contextData.get(LoggingConstants.QUERY_STRING_KEY)).isEqualTo("page=1");
        assertThat(contextData.get(LoggingConstants.STATUS_KEY)).isEqualTo("200");
        assertThat(contextData.get(LoggingConstants.DURATION_MS_KEY)).isNotBlank();
        assertThat(response.getContentAsString()).isEqualTo("{\"ok\":true}");
        assertThat(LoggingContext.get(LoggingConstants.REQUEST_ID_KEY)).isNull();
        assertThat(LoggingContext.get(LoggingConstants.METHOD_KEY)).isNull();
        assertThat(LoggingContext.get(LoggingConstants.URI_KEY)).isNull();
        assertThat(LoggingContext.get(LoggingConstants.STATUS_KEY)).isNull();
        assertThat(LoggingContext.get(LoggingConstants.DURATION_MS_KEY)).isNull();
    }

    private static final class TestAppender extends AbstractAppender {

        private final AtomicReference<LogEvent> capturedEvent;

        private TestAppender(String name, AtomicReference<LogEvent> capturedEvent) {
            super(name, null, null, false, Property.EMPTY_ARRAY);
            this.capturedEvent = capturedEvent;
        }

        @Override
        public void append(LogEvent event) {
            capturedEvent.set(event.toImmutable());
        }
    }
}
