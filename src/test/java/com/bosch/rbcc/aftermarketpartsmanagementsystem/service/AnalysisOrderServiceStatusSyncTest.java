package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.AnalysisOrder;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.Part;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.AnalysisOrderRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.PartRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.ReturnOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalysisOrderServiceStatusSyncTest {

    @Mock AnalysisOrderRepository analysisOrderRepo;
    @Mock PartRepository partRepo;
    @Mock ReturnOrderRepository returnOrderRepo;

    @InjectMocks AnalysisOrderService service;

    private AnalysisOrder order;
    private Part part1, part2;

    @BeforeEach
    void setUp() {
        order = AnalysisOrder.builder()
                .id("ao-1").orderId("order-1").analyst("analyst1")
                .status("in_detailed_analysis").build();

        part1 = Part.builder().id("p-1").orderId("order-1").analyst("analyst1")
                .isSample(1).status("in_detailed_analysis").build();
        part2 = Part.builder().id("p-2").orderId("order-1").analyst("analyst1")
                .isSample(0).status("in_initial_analysis").build();
    }

    @Test
    void scrap_shouldUpdateAllPartsToScrapInProgress() {
        when(analysisOrderRepo.findById("ao-1")).thenReturn(Optional.of(order));
        when(partRepo.findByOrderIdAndAnalyst("order-1", "analyst1"))
                .thenReturn(List.of(part1, part2));
        when(analysisOrderRepo.save(any())).thenReturn(order);
        when(returnOrderRepo.findById(any())).thenReturn(Optional.empty());

        service.scrap("ao-1");

        assertThat(part1.getStatus()).isEqualTo("scrap_in_progress");
        assertThat(part2.getStatus()).isEqualTo("scrap_in_progress");
        verify(partRepo, times(2)).save(any(Part.class));
    }
}
