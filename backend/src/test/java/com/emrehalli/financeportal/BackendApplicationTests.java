package com.emrehalli.financeportal;

import com.emrehalli.financeportal.news.scheduler.NewsScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
class BackendApplicationTests {

    @MockBean
    private NewsScheduler newsScheduler;

    @Test
    void contextLoads() {
    }

}



