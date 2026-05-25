package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.AnalysisOrderDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.AnalysisOrder;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.AnalysisOrderRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.PartRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.ReturnOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalysisOrderServiceGetOrCreateTest {

    @Mock private AnalysisOrderRepository analysisOrderRepo;
    @Mock private PartRepository partRepo;
    @Mock private ReturnOrderRepository returnOrderRepo;
    @Mock private ReturnOrderService returnOrderService;

    @InjectMocks
    private AnalysisOrderService service;

    @Test
    void getOrCreate_newAo_createsWithPendingSamplingStatus() {
        when(analysisOrderRepo.findByOrderIdAndAnalyst("order-1", "analyst1"))
                .thenReturn(Optional.empty());
        ArgumentCaptor<AnalysisOrder> aoCaptor = ArgumentCaptor.forClass(AnalysisOrder.class);
        when(analysisOrderRepo.save(aoCaptor.capture())).thenAnswer(i -> i.getArgument(0));

        service.getOrCreate("order-1", "analyst1");

        assertEquals("pending_sampling", aoCaptor.getValue().getStatus());
        assertEquals("order-1", aoCaptor.getValue().getOrderId());
        assertEquals("analyst1", aoCaptor.getValue().getAnalyst());
    }

    @Test
    void getOrCreate_existingAo_returnsExistingWithoutSaving() {
        AnalysisOrder existing = AnalysisOrder.builder()
                .id("ao-1").orderId("order-1").analyst("analyst1")
                .status("in_detailed_analysis").build();
        when(analysisOrderRepo.findByOrderIdAndAnalyst("order-1", "analyst1"))
                .thenReturn(Optional.of(existing));

        AnalysisOrderDTO result = service.getOrCreate("order-1", "analyst1");

        assertEquals("ao-1", result.getId());
        verify(analysisOrderRepo, never()).save(any());
    }
}
