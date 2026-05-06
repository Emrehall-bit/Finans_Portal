package com.emrehalli.financeportal.news.scheduler;

import com.emrehalli.financeportal.news.dto.response.NewsSyncResponseDto;
import com.emrehalli.financeportal.news.enums.NewsProviderType;
import com.emrehalli.financeportal.news.provider.investing.InvestingNewsProperties;
import com.emrehalli.financeportal.news.service.NewsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsSchedulerTest {

    @Mock
    private NewsService newsService;

    @Test
    void startupRunsOnlyConfiguredProviders() {
        InvestingNewsProperties investingProps = new InvestingNewsProperties();
        investingProps.setEnabled(true);

        when(newsService.syncProvider(NewsProviderType.FINNHUB)).thenReturn(NewsSyncResponseDto.builder().provider("FINNHUB").build());
        when(newsService.syncProvider(NewsProviderType.INVESTING_RSS)).thenReturn(NewsSyncResponseDto.builder().provider("INVESTING_RSS").build());

        NewsScheduler scheduler = new NewsScheduler(newsService, investingProps);

        scheduler.loadOnStartup();

        InOrder inOrder = inOrder(newsService);
        inOrder.verify(newsService).syncProvider(NewsProviderType.FINNHUB);
        inOrder.verify(newsService).syncProvider(NewsProviderType.INVESTING_RSS);
    }

    @Test
    void continuesRunningRemainingProvidersWhenOneFails() {
        InvestingNewsProperties investingProps = new InvestingNewsProperties();
        investingProps.setEnabled(true);
        NewsScheduler scheduler = new NewsScheduler(newsService, investingProps);

        when(newsService.syncProvider(NewsProviderType.FINNHUB)).thenThrow(new RuntimeException("downstream"));
        when(newsService.syncProvider(NewsProviderType.INVESTING_RSS)).thenReturn(NewsSyncResponseDto.builder()
                .provider("INVESTING_RSS")
                .fetchedCount(1)
                .savedCount(1)
                .existingCount(0)
                .invalidCount(0)
                .build());

        scheduler.syncPrimaryProviders();

        InOrder inOrder = inOrder(newsService);
        inOrder.verify(newsService).syncProvider(NewsProviderType.FINNHUB);
        inOrder.verify(newsService).syncProvider(NewsProviderType.INVESTING_RSS);
    }

    @Test
    void secondaryRssScheduleRunsAaSync() {
        InvestingNewsProperties investingProps = new InvestingNewsProperties();
        investingProps.setEnabled(true);
        NewsScheduler scheduler = new NewsScheduler(newsService, investingProps);

        when(newsService.syncProvider(NewsProviderType.AA_RSS)).thenReturn(NewsSyncResponseDto.builder().provider("AA_RSS").build());

        scheduler.syncSecondaryRssProviders();

        verify(newsService).syncProvider(NewsProviderType.AA_RSS);
    }
}
