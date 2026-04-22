package com.emrehalli.financeportal;

import com.emrehalli.financeportal.market.scheduler.MarketDataScheduler;
import com.emrehalli.financeportal.market.scheduler.MarketEventScheduler;
import com.emrehalli.financeportal.news.scheduler.NewsScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
class BackendApplicationTests {

    @MockBean
    private MarketDataScheduler marketDataScheduler;

    @MockBean
    private MarketEventScheduler marketEventScheduler;

    @MockBean
    private NewsScheduler newsScheduler;

    @Test
    void contextLoads() {
    }

}
