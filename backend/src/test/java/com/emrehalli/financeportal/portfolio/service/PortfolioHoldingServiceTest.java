package com.emrehalli.financeportal.portfolio.service;

import com.emrehalli.financeportal.portfolio.dto.CreatePortfolioHoldingRequest;
import com.emrehalli.financeportal.portfolio.dto.PortfolioHoldingDto;
import com.emrehalli.financeportal.portfolio.dto.PortfolioSummaryResponse;
import com.emrehalli.financeportal.portfolio.dto.UpdatePortfolioHoldingRequest;
import com.emrehalli.financeportal.portfolio.entity.Portfolio;
import com.emrehalli.financeportal.portfolio.entity.PortfolioHolding;
import com.emrehalli.financeportal.portfolio.entity.PortfolioVisibility;
import com.emrehalli.financeportal.portfolio.enums.PriceStatus;
import com.emrehalli.financeportal.portfolio.enums.SummaryStatus;
import com.emrehalli.financeportal.portfolio.repository.PortfolioHoldingRepository;
import com.emrehalli.financeportal.portfolio.repository.PortfolioRepository;
import com.emrehalli.financeportal.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioHoldingServiceTest {

    @Mock
    private PortfolioHoldingRepository portfolioHoldingRepository;

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private PortfolioPriceResolver portfolioPriceResolver;

    @InjectMocks
    private PortfolioHoldingService portfolioHoldingService;

    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        portfolio = Portfolio.builder()
                .id(10L)
                .portfolioName("Ana Portfoy")
                .visibilityStatus(PortfolioVisibility.PRIVATE)
                .createdAt(LocalDateTime.of(2026, 4, 22, 10, 0))
                .user(User.builder().id(5L).fullName("Test User").email("test@example.com").build())
                .build();
    }

    @Test
    void createHolding_whenSameInstrumentAlreadyExists_savesAnotherHoldingRow() {
        CreatePortfolioHoldingRequest request = new CreatePortfolioHoldingRequest();
        request.setInstrumentCode("thyao");
        request.setQuantity(new BigDecimal("10"));
        request.setBuyPrice(new BigDecimal("100"));

        when(portfolioRepository.findById(10L)).thenReturn(Optional.of(portfolio));
        when(portfolioHoldingRepository.save(any(PortfolioHolding.class))).thenAnswer(invocation -> {
            PortfolioHolding saved = invocation.getArgument(0);
            saved.setId(77L);
            return saved;
        });
        when(portfolioPriceResolver.resolveCurrentPriceWithFallback("THYAO"))
                .thenReturn(PriceResolutionResult.available(new BigDecimal("110"), PriceStatus.CACHED, LocalDateTime.of(2026, 4, 22, 11, 0)));

        PortfolioHoldingDto result = portfolioHoldingService.createHolding(10L, request);

        assertEquals(77L, result.getHoldingId());
        assertEquals("THYAO", result.getInstrumentCode());
        verify(portfolioHoldingRepository).save(any(PortfolioHolding.class));
    }

    @Test
    void updateHolding_updatesQuantityAndBuyPrice() {
        PortfolioHolding holding = holding(7L, "THYAO", "3", "200");
        UpdatePortfolioHoldingRequest request = new UpdatePortfolioHoldingRequest();
        request.setQuantity(new BigDecimal("5"));
        request.setBuyPrice(new BigDecimal("210"));

        when(portfolioRepository.existsById(10L)).thenReturn(true);
        when(portfolioHoldingRepository.findByIdAndPortfolioId(7L, 10L)).thenReturn(Optional.of(holding));
        when(portfolioHoldingRepository.save(any(PortfolioHolding.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(portfolioPriceResolver.resolveCurrentPriceWithFallback("THYAO"))
                .thenReturn(PriceResolutionResult.available(new BigDecimal("220"), PriceStatus.LIVE, LocalDateTime.of(2026, 4, 22, 11, 0)));

        PortfolioHoldingDto result = portfolioHoldingService.updateHolding(10L, 7L, request);

        assertEquals(new BigDecimal("5"), result.getQuantity());
        assertEquals(new BigDecimal("210"), result.getBuyPrice());
        assertEquals(new BigDecimal("220"), result.getCurrentPrice());
        assertNotNull(result.getUpdatedAt());
    }

    @Test
    void deleteHolding_deletesResolvedHolding() {
        PortfolioHolding holding = holding(7L, "THYAO", "3", "200");

        when(portfolioRepository.existsById(10L)).thenReturn(true);
        when(portfolioHoldingRepository.findByIdAndPortfolioId(7L, 10L)).thenReturn(Optional.of(holding));

        portfolioHoldingService.deleteHolding(10L, 7L);

        verify(portfolioHoldingRepository).delete(holding);
    }

    @Test
    void getPortfolioSummary_whenSomePricesMissing_returnsPartialSummary() {
        PortfolioHolding valuedHolding = holding(1L, "THYAO", "10", "100");
        PortfolioHolding missingHolding = holding(2L, "ASELS", "4", "50");

        when(portfolioRepository.existsById(10L)).thenReturn(true);
        when(portfolioHoldingRepository.findByPortfolioId(10L)).thenReturn(List.of(valuedHolding, missingHolding));
        when(portfolioPriceResolver.resolveCurrentPriceWithFallback("THYAO"))
                .thenReturn(PriceResolutionResult.available(new BigDecimal("120"), PriceStatus.LIVE, LocalDateTime.of(2026, 4, 22, 11, 0)));
        when(portfolioPriceResolver.resolveCurrentPriceWithFallback("ASELS"))
                .thenReturn(PriceResolutionResult.unavailable());

        PortfolioSummaryResponse result = portfolioHoldingService.getPortfolioSummary(10L);

        assertEquals(new BigDecimal("1200"), result.getTotalCost());
        assertEquals(new BigDecimal("1200"), result.getCurrentValue());
        assertEquals(new BigDecimal("200"), result.getProfitLoss());
        assertEquals(new BigDecimal("20.0000"), result.getProfitLossPercent());
        assertEquals(SummaryStatus.PARTIAL, result.getSummaryStatus());
        assertEquals(1, result.getMissingPriceCount());
    }

    @Test
    void getHoldingsByPortfolioId_mapsValuationAvailability() {
        PortfolioHolding holding = holding(1L, "THYAO", "2", "100");

        when(portfolioRepository.existsById(10L)).thenReturn(true);
        when(portfolioHoldingRepository.findByPortfolioId(10L)).thenReturn(List.of(holding));
        when(portfolioPriceResolver.resolveCurrentPriceWithFallback("THYAO"))
                .thenReturn(PriceResolutionResult.unavailable());

        List<PortfolioHoldingDto> result = portfolioHoldingService.getHoldingsByPortfolioId(10L);

        assertEquals(1, result.size());
        assertFalse(result.get(0).isValuationAvailable());
        assertEquals(PriceStatus.UNAVAILABLE, result.get(0).getPriceStatus());
        assertEquals("THYAO", result.get(0).getInstrumentCode());
    }

    @Test
    void createHolding_normalizesInstrumentCodeBeforeSaving() {
        CreatePortfolioHoldingRequest request = new CreatePortfolioHoldingRequest();
        request.setInstrumentCode("  thyao ");
        request.setQuantity(new BigDecimal("10"));
        request.setBuyPrice(new BigDecimal("100"));

        when(portfolioRepository.findById(10L)).thenReturn(Optional.of(portfolio));
        when(portfolioHoldingRepository.save(any(PortfolioHolding.class))).thenAnswer(invocation -> {
            PortfolioHolding saved = invocation.getArgument(0);
            saved.setId(99L);
            return saved;
        });
        when(portfolioPriceResolver.resolveCurrentPriceWithFallback("THYAO"))
                .thenReturn(PriceResolutionResult.available(new BigDecimal("105"), PriceStatus.CACHED, LocalDateTime.of(2026, 4, 22, 11, 0)));

        PortfolioHoldingDto result = portfolioHoldingService.createHolding(10L, request);
        ArgumentCaptor<PortfolioHolding> captor = ArgumentCaptor.forClass(PortfolioHolding.class);

        verify(portfolioHoldingRepository).save(captor.capture());
        assertEquals("THYAO", captor.getValue().getInstrumentCode());
        assertTrue(result.isValuationAvailable());
        assertEquals(new BigDecimal("1050"), result.getCurrentValue());
    }

    private PortfolioHolding holding(Long id, String instrumentCode, String quantity, String buyPrice) {
        return PortfolioHolding.builder()
                .id(id)
                .portfolio(portfolio)
                .instrumentCode(instrumentCode)
                .quantity(new BigDecimal(quantity))
                .buyPrice(new BigDecimal(buyPrice))
                .createdAt(LocalDateTime.of(2026, 4, 20, 10, 0))
                .updatedAt(LocalDateTime.of(2026, 4, 20, 10, 0))
                .build();
    }
}
