package com.emrehalli.financeportal.portfolio.service;

import com.emrehalli.financeportal.common.exception.ResourceNotFoundException;
import com.emrehalli.financeportal.portfolio.dto.CreatePortfolioRequest;
import com.emrehalli.financeportal.portfolio.dto.PortfolioResponseDto;
import com.emrehalli.financeportal.portfolio.dto.UpdatePortfolioRequest;
import com.emrehalli.financeportal.portfolio.entity.Portfolio;
import com.emrehalli.financeportal.portfolio.entity.PortfolioVisibility;
import com.emrehalli.financeportal.portfolio.repository.PortfolioRepository;
import com.emrehalli.financeportal.user.entity.User;
import com.emrehalli.financeportal.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final UserRepository userRepository;

    public PortfolioService(PortfolioRepository portfolioRepository,
                            UserRepository userRepository) {
        this.portfolioRepository = portfolioRepository;
        this.userRepository = userRepository;
    }

    public PortfolioResponseDto createPortfolio(Long userId, CreatePortfolioRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        Portfolio portfolio = Portfolio.builder()
                .portfolioName(request.getPortfolioName())
                .visibilityStatus(request.getVisibilityStatus())
                .createdAt(LocalDateTime.now())
                .user(user)
                .build();

        return toResponseDto(portfolioRepository.save(portfolio));
    }

    public List<PortfolioResponseDto> getPortfoliosByUserId(Long userId) {
        return portfolioRepository.findByUserId(userId)
                .stream()
                .map(this::toResponseDto)
                .toList();
    }

    public PortfolioResponseDto updatePortfolio(Long portfolioId, UpdatePortfolioRequest request) {
        Portfolio portfolio = getPortfolioEntityById(portfolioId);
        portfolio.setPortfolioName(request.getPortfolioName());
        portfolio.setVisibilityStatus(request.getVisibilityStatus());
        return toResponseDto(portfolioRepository.save(portfolio));
    }

    public Portfolio getPortfolioEntityById(Long portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found with id: " + portfolioId));

        if (portfolio.getVisibilityStatus() == null) {
            portfolio.setVisibilityStatus(PortfolioVisibility.PRIVATE);
        }
        return portfolio;
    }

    private PortfolioResponseDto toResponseDto(Portfolio portfolio) {
        return PortfolioResponseDto.builder()
                .portfolioId(portfolio.getId())
                .portfolioName(portfolio.getPortfolioName())
                .visibilityStatus(portfolio.getVisibilityStatus())
                .createdAt(portfolio.getCreatedAt())
                .userId(portfolio.getUser() != null ? portfolio.getUser().getId() : null)
                .build();
    }
}



