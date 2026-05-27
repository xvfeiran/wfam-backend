package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.Part;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.ReturnOrder;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.AnalysisOrderRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.CustomerRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.PartRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.ReturnOrderRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.config.ExportProperties;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.service.excel.ReturnOrderExcelHandler;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReturnOrderServiceSubmitTest {

    @Mock private ReturnOrderRepository orderRepo;
    @Mock private PartRepository partRepo;
    @Mock private CustomerRepository customerRepo;
    @Mock private AnalysisOrderRepository analysisOrderRepo;
    @Mock private ReturnOrderExcelHandler excelHandler;
    @Mock private ExportProperties exportProperties;
    @Mock private EntityManager entityManager;

    @InjectMocks
    private ReturnOrderService service;

    @Test
    void submit_emptyOrder_throwsBadRequest() {
        ReturnOrder order = ReturnOrder.builder().id("order-1").status("draft").build();
        when(orderRepo.findById("order-1")).thenReturn(Optional.of(order));
        when(partRepo.findByOrderId("order-1")).thenReturn(Collections.emptyList());

        assertThrows(ResponseStatusException.class, () -> service.submit("order-1"));
    }

    @Test
    void submit_notDraft_throwsBadRequest() {
        ReturnOrder order = ReturnOrder.builder().id("order-1").status("submitted").build();
        when(orderRepo.findById("order-1")).thenReturn(Optional.of(order));

        assertThrows(ResponseStatusException.class, () -> service.submit("order-1"));
    }

    @Test
    void submit_withAftermarketParts_createsAnalysisOrdersPerAnalyst() {
        ReturnOrder order = ReturnOrder.builder().id("order-1").status("draft").complaintType("BA40").build();
        when(orderRepo.findById("order-1")).thenReturn(Optional.of(order));

        Part part1 = Part.builder().id("p1").orderId("order-1").analyst("analyst1").build();
        Part part2 = Part.builder().id("p2").orderId("order-1").analyst("analyst1").build();
        Part part3 = Part.builder().id("p3").orderId("order-1").analyst("analyst2").build();
        when(partRepo.findByOrderId("order-1")).thenReturn(List.of(part1, part2, part3));

        // No existing analysis orders
        when(analysisOrderRepo.findByOrderIdAndAnalyst(anyString(), anyString())).thenReturn(Optional.empty());
        when(analysisOrderRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.submit("order-1");

        // Should create exactly 2 analysis orders (one per unique analyst)
        ArgumentCaptor<com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.AnalysisOrder> aoCaptor =
                ArgumentCaptor.forClass(com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.AnalysisOrder.class);
        verify(analysisOrderRepo, times(2)).save(aoCaptor.capture());

        var saved = aoCaptor.getAllValues();
        // analyst1's order should be pending_sampling (aftermarket)
        assertEquals("pending_sampling", saved.stream().filter(a -> "analyst1".equals(a.getAnalyst())).findFirst().get().getStatus());
        // analyst2's order should be pending_sampling (aftermarket)
        assertEquals("pending_sampling", saved.stream().filter(a -> "analyst2".equals(a.getAnalyst())).findFirst().get().getStatus());
    }

    @Test
    void submit_withZeroKmParts_createsAnalysisCompleted() {
        ReturnOrder order = ReturnOrder.builder().id("order-1").status("draft").complaintType("BA20").build();
        when(orderRepo.findById("order-1")).thenReturn(Optional.of(order));

        Part part1 = Part.builder().id("p1").orderId("order-1").analyst("analyst1").build();
        when(partRepo.findByOrderId("order-1")).thenReturn(List.of(part1));

        when(analysisOrderRepo.findByOrderIdAndAnalyst("order-1", "analyst1")).thenReturn(Optional.empty());
        when(analysisOrderRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.submit("order-1");

        ArgumentCaptor<com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.AnalysisOrder> aoCaptor =
                ArgumentCaptor.forClass(com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.AnalysisOrder.class);
        verify(analysisOrderRepo, times(1)).save(aoCaptor.capture());
        assertEquals("analysis_completed", aoCaptor.getValue().getStatus());
    }

    @Test
    void submit_existingAnalysisOrder_notRecreated() {
        ReturnOrder order = ReturnOrder.builder().id("order-1").status("draft").complaintType("BA40").build();
        when(orderRepo.findById("order-1")).thenReturn(Optional.of(order));

        Part part1 = Part.builder().id("p1").orderId("order-1").analyst("analyst1").build();
        when(partRepo.findByOrderId("order-1")).thenReturn(List.of(part1));

        // Existing analysis order
        var existingAo = com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.AnalysisOrder.builder()
                .id("ao-1").orderId("order-1").analyst("analyst1").status("pending_sampling").build();
        when(analysisOrderRepo.findByOrderIdAndAnalyst("order-1", "analyst1")).thenReturn(Optional.of(existingAo));

        service.submit("order-1");

        verify(analysisOrderRepo, never()).save(any());
    }
}
