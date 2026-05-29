package com.emrehalli.financeportal.technicalanalysis.drawing.repository;

import com.emrehalli.financeportal.technicalanalysis.drawing.entity.ChartDrawing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChartDrawingRepository extends JpaRepository<ChartDrawing, Long> {

    List<ChartDrawing> findByUserIdAndInstrumentIdAndTimeframe(Long userId, Long instrumentId, String timeframe);

    Optional<ChartDrawing> findByIdAndUserId(Long id, Long userId);

    void deleteByIdAndUserId(Long id, Long userId);

    List<ChartDrawing> findByIsAlertLinkedTrueAndLinkedAlertIdIsNotNull();
}

