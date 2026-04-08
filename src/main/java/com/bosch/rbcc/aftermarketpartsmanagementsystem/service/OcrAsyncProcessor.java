package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.OcrResultDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.OcrTask;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.OcrTaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Random;

/**
 * OCR 异步处理器 —— 独立 Bean 以保证 @Async 代理生效。
 */
@Slf4j
@Component
public class OcrAsyncProcessor {

    private static final Random RANDOM = new Random();
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final double TASK_FAIL_RATE = 0.3;

    private final OcrTaskRepository ocrTaskRepo;
    private final ObjectMapper objectMapper;

    // @Lazy 打破与 OcrService 的循环依赖
    @Lazy
    @Autowired
    private OcrService ocrService;

    public OcrAsyncProcessor(OcrTaskRepository ocrTaskRepo, ObjectMapper objectMapper) {
        this.ocrTaskRepo = ocrTaskRepo;
        this.objectMapper = objectMapper;
    }

    @Async("ocrTaskExecutor")
    public void process(String taskId) {
        markProcessing(taskId);

        try {
            log.info("OCR 识别开始: taskId={}, 延迟 30s", taskId);
            Thread.sleep(30_000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            markFailed(taskId, "识别被中断");
            return;
        }

        if (RANDOM.nextDouble() < TASK_FAIL_RATE) {
            log.warn("OCR 识别随机失败（降级）: taskId={}", taskId);
            markFailed(taskId, "OCR 服务暂时不可用，请手动填写");
            return;
        }

        try {
            String resultJson = objectMapper.writeValueAsString(generateMockResult());
            markSuccessAndWritePart(taskId, resultJson);
            log.info("OCR 识别成功: taskId={}", taskId);
        } catch (Exception e) {
            log.error("OCR 结果序列化失败: taskId={}", taskId, e);
            markFailed(taskId, "结果处理异常");
        }
    }

    @Transactional
    public void markProcessing(String taskId) {
        updateTask(taskId, OcrTask.STATUS_PROCESSING, null, null);
    }

    /**
     * 标记任务成功，若已绑定 Part 则同事务内写入 Part 表。
     */
    @Transactional
    public void markSuccessAndWritePart(String taskId, String resultJson) {
        OcrTask task = updateTask(taskId, OcrTask.STATUS_SUCCESS, resultJson, null);

        if (task != null && task.getPartId() != null) {
            // Part 已绑定（编辑模式 or 新建后已保存并绑定）→ 直接写入
            ocrService.writeOcrResultToPart(resultJson, task.getPartId());
        } else {
            // Part 尚未创建（新建模式且用户还未提交）
            // 等 Part 创建时 PartService 会调用 OcrService.bindTaskToPart() 触发写入
            log.info("OCR 成功但 Part 尚未创建，等待绑定: taskId={}", taskId);
        }
    }

    @Transactional
    public void markFailed(String taskId, String errorMessage) {
        updateTask(taskId, OcrTask.STATUS_FAILED, null, errorMessage);
    }

    // ── 私有方法 ──────────────────────────────────────────────────

    private OcrTask updateTask(String taskId, String status, String resultJson, String errorMessage) {
        return ocrTaskRepo.findById(taskId).map(task -> {
            task.setStatus(status);
            task.setResultJson(resultJson);
            task.setErrorMessage(errorMessage);
            return ocrTaskRepo.save(task);
        }).orElse(null);
    }

    private OcrResultDTO generateMockResult() {
        LocalDate now = LocalDate.now();
        LocalDate productionDate = now.minusMonths(RANDOM.nextInt(12) + 6);
        LocalDate purchaseDate   = productionDate.plusMonths(RANDOM.nextInt(3) + 1);
        LocalDate failureDate    = now.minusDays(RANDOM.nextInt(30) + 1);

        String[] vins = {
            "LSVAB2183E2123456", "LSVCD4291F3456789", "WVWEF5382G1234567",
            "WBA3A5C50EF123456", "LSVAA4381E1765432", "LHGTD3845K2345678"
        };
        String[] descriptions = {
            "发动机异响，怠速不稳", "怠速抖动，加速无力", "传感器读数不准确",
            "电路板烧毁", "连接器松动导致断电", "空调制冷效果差",
            "转向系统异响", "制动系统失灵", "变速箱换挡顿挫", "电子系统故障灯亮"
        };

        return OcrResultDTO.builder()
                .vehicleProductionDate(RANDOM.nextDouble() < 0.7  ? productionDate.format(DATE_FMT) : null)
                .vehiclePurchaseDate(RANDOM.nextDouble()   < 0.7  ? purchaseDate.format(DATE_FMT)   : null)
                .vehicleFailureDate(RANDOM.nextDouble()    < 0.8  ? failureDate.format(DATE_FMT)    : null)
                .vehicleVIN(RANDOM.nextDouble()            < 0.9  ? vins[RANDOM.nextInt(vins.length)] : null)
                .vehicleMileage(RANDOM.nextDouble()        < 0.75 ? 5000 + RANDOM.nextInt(50000)    : null)
                .customerDescription(RANDOM.nextDouble()  < 0.8  ? descriptions[RANDOM.nextInt(descriptions.length)] : null)
                .build();
    }
}
