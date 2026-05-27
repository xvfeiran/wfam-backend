package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.AnalysisOrder;
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

    // --- submit() tests ---

    @Test
    void submit_notDraft_throwsBadRequest() {
        ReturnOrder order = ReturnOrder.builder().id("order-1").status("submitted").build();
        when(orderRepo.findById("order-1")).thenReturn(Optional.of(order));

        assertThrows(ResponseStatusException.class, () -> service.submit("order-1"));
    }

    // --- endEntry() tests ---

    @Test
    void endEntry_emptyOrder_throwsBadRequest() {
        ReturnOrder order = ReturnOrder.builder().id("order-1").status("submitted").build();
        when(orderRepo.findById("order-1")).thenReturn(Optional.of(order));
        when(partRepo.findByOrderId("order-1")).thenReturn(Collections.emptyList());

        assertThrows(ResponseStatusException.class, () -> service.endEntry("order-1"));
    }

    @Test
    void endEntry_notSubmitted_throwsBadRequest() {
        ReturnOrder order = ReturnOrder.builder().id("order-1").status("draft").build();
        when(orderRepo.findById("order-1")).thenReturn(Optional.of(order));

        assertThrows(ResponseStatusException.class, () -> service.endEntry("order-1"));
    }

    @Test
    void endEntry_withAftermarketParts_createsAnalysisOrdersAndSetsRegistered() {
        ReturnOrder order = ReturnOrder.builder().id("order-1").status("submitted").complaintType("BA40").build();
        when(orderRepo.findById("order-1")).thenReturn(Optional.of(order));

        Part part1 = Part.builder().id("p1").orderId("order-1").analyst("analyst1").build();
        Part part2 = Part.builder().id("p2").orderId("order-1").analyst("analyst1").build();
        Part part3 = Part.builder().id("p3").orderId("order-1").analyst("analyst2").build();
        when(partRepo.findByOrderId("order-1")).thenReturn(List.of(part1, part2, part3));

        when(analysisOrderRepo.countByOrderId("order-1")).thenReturn(0L);
        when(analysisOrderRepo.findByOrderIdAndAnalyst(anyString(), anyString())).thenReturn(Optional.empty());
        when(analysisOrderRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.endEntry("order-1");

        ArgumentCaptor<AnalysisOrder> aoCaptor = ArgumentCaptor.forClass(AnalysisOrder.class);
        verify(analysisOrderRepo, times(2)).save(aoCaptor.capture());

        var saved = aoCaptor.getAllValues();
        assertEquals("pending_sampling", saved.stream().filter(a -> "analyst1".equals(a.getAnalyst())).findFirst().get().getStatus());
        assertEquals("pending_sampling", saved.stream().filter(a -> "analyst2".equals(a.getAnalyst())).findFirst().get().getStatus());

        // Status should be changed to registered
        verify(orderRepo, times(1)).save(argThat(o -> "registered".equals(o.getStatus())));
    }

    @Test
    void endEntry_withZeroKmParts_createsAnalysisCompleted() {
        ReturnOrder order = ReturnOrder.builder().id("order-1").status("submitted").complaintType("BA20").build();
        when(orderRepo.findById("order-1")).thenReturn(Optional.of(order));

        Part part1 = Part.builder().id("p1").orderId("order-1").analyst("analyst1").build();
        when(partRepo.findByOrderId("order-1")).thenReturn(List.of(part1));

        when(analysisOrderRepo.countByOrderId("order-1")).thenReturn(0L);
        when(analysisOrderRepo.findByOrderIdAndAnalyst("order-1", "analyst1")).thenReturn(Optional.empty());
        when(analysisOrderRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.endEntry("order-1");

        ArgumentCaptor<AnalysisOrder> aoCaptor = ArgumentCaptor.forClass(AnalysisOrder.class);
        verify(analysisOrderRepo, times(1)).save(aoCaptor.capture());
        assertEquals("analysis_completed", aoCaptor.getValue().getStatus());
    }

    @Test
    void endEntry_existingAnalysisOrder_notRecreated() {
        ReturnOrder order = ReturnOrder.builder().id("order-1").status("submitted").complaintType("BA40").build();
        when(orderRepo.findById("order-1")).thenReturn(Optional.of(order));

        Part part1 = Part.builder().id("p1").orderId("order-1").analyst("analyst1").build();
        when(partRepo.findByOrderId("order-1")).thenReturn(List.of(part1));

        // Analysis orders already exist
        when(analysisOrderRepo.countByOrderId("order-1")).thenReturn(1L);

        service.endEntry("order-1");

        verify(analysisOrderRepo, never()).save(any());
    }
}
