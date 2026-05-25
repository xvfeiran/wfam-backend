package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.AnalysisOrderDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.AnalysisOrder;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.ReturnOrder;
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
    void getOrCreate_zeroKmOrder_createsAoWithAnalysisCompletedStatus() {
        ReturnOrder order = ReturnOrder.builder()
                .id("order-1").orderNumber("RO-001").complaintType("BA20").build();
        when(analysisOrderRepo.findByOrderIdAndAnalyst("order-1", "analyst1"))
                .thenReturn(Optional.empty());
        when(returnOrderRepo.findById("order-1")).thenReturn(Optional.of(order));
        ArgumentCaptor<AnalysisOrder> aoCaptor = ArgumentCaptor.forClass(AnalysisOrder.class);
        when(analysisOrderRepo.save(aoCaptor.capture())).thenAnswer(i -> i.getArgument(0));

        service.getOrCreate("order-1", "analyst1");

        assertEquals("analysis_completed", aoCaptor.getValue().getStatus());
    }

    @Test
    void getOrCreate_ba35ZeroKmOrder_createsAoWithAnalysisCompletedStatus() {
        ReturnOrder order = ReturnOrder.builder()
                .id("order-2").orderNumber("RO-002").complaintType("BA35").build();
        when(analysisOrderRepo.findByOrderIdAndAnalyst("order-2", "analyst1"))
                .thenReturn(Optional.empty());
        when(returnOrderRepo.findById("order-2")).thenReturn(Optional.of(order));
        ArgumentCaptor<AnalysisOrder> aoCaptor = ArgumentCaptor.forClass(AnalysisOrder.class);
        when(analysisOrderRepo.save(aoCaptor.capture())).thenAnswer(i -> i.getArgument(0));

        service.getOrCreate("order-2", "analyst1");

        assertEquals("analysis_completed", aoCaptor.getValue().getStatus());
    }

    @Test
    void getOrCreate_ba40AftermarketOrder_createsAoWithPendingSamplingStatus() {
        ReturnOrder order = ReturnOrder.builder()
                .id("order-3").orderNumber("RO-003").complaintType("BA40").build();
        when(analysisOrderRepo.findByOrderIdAndAnalyst("order-3", "analyst1"))
                .thenReturn(Optional.empty());
        when(returnOrderRepo.findById("order-3")).thenReturn(Optional.of(order));
        ArgumentCaptor<AnalysisOrder> aoCaptor = ArgumentCaptor.forClass(AnalysisOrder.class);
        when(analysisOrderRepo.save(aoCaptor.capture())).thenAnswer(i -> i.getArgument(0));

        service.getOrCreate("order-3", "analyst1");

        assertEquals("pending_sampling", aoCaptor.getValue().getStatus());
    }

    @Test
    void getOrCreate_ba41AftermarketOrder_createsAoWithPendingSamplingStatus() {
        ReturnOrder order = ReturnOrder.builder()
                .id("order-4").orderNumber("RO-004").complaintType("BA41").build();
        when(analysisOrderRepo.findByOrderIdAndAnalyst("order-4", "analyst1"))
                .thenReturn(Optional.empty());
        when(returnOrderRepo.findById("order-4")).thenReturn(Optional.of(order));
        ArgumentCaptor<AnalysisOrder> aoCaptor = ArgumentCaptor.forClass(AnalysisOrder.class);
        when(analysisOrderRepo.save(aoCaptor.capture())).thenAnswer(i -> i.getArgument(0));

        service.getOrCreate("order-4", "analyst1");

        assertEquals("pending_sampling", aoCaptor.getValue().getStatus());
    }

    @Test
    void getOrCreate_orderNotFound_createsAoWithPendingSamplingStatus() {
        when(analysisOrderRepo.findByOrderIdAndAnalyst("order-x", "analyst1"))
                .thenReturn(Optional.empty());
        when(returnOrderRepo.findById("order-x")).thenReturn(Optional.empty());
        ArgumentCaptor<AnalysisOrder> aoCaptor = ArgumentCaptor.forClass(AnalysisOrder.class);
        when(analysisOrderRepo.save(aoCaptor.capture())).thenAnswer(i -> i.getArgument(0));

        service.getOrCreate("order-x", "analyst1");

        assertEquals("pending_sampling", aoCaptor.getValue().getStatus());
    }

    @Test
    void getOrCreate_existingAo_returnsExistingWithoutSaving() {
        AnalysisOrder existing = AnalysisOrder.builder()
                .id("ao-1").orderId("order-1").analyst("analyst1")
                .status("in_detailed_analysis").build();
        when(analysisOrderRepo.findByOrderIdAndAnalyst("order-1", "analyst1"))
                .thenReturn(Optional.of(existing));
        when(returnOrderRepo.findById("order-1")).thenReturn(Optional.empty());

        AnalysisOrderDTO result = service.getOrCreate("order-1", "analyst1");

        assertEquals("ao-1", result.getId());
        verify(analysisOrderRepo, never()).save(any());
    }
}
