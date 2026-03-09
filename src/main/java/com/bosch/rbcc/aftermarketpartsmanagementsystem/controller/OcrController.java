package com.bosch.rbcc.aftermarketpartsmanagementsystem.controller;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.OcrResultDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * OCR识别控制器
 * 提供客诉信息卡OCR识别功能的Mock接口
 */
@RestController
@RequestMapping("/api/v1/ocr")
@Slf4j
@Tag(name = "OCR Recognition", description = "客诉信息卡OCR识别接口")
public class OcrController {

    private static final Executor executor = Executors.newFixedThreadPool(10);
    private static final Random random = new Random();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * OCR识别接口
     * 模拟5-15秒的随机延迟后返回识别结果
     */
    @PostMapping("/recognize")
    @Operation(summary = "OCR识别", description = "识别客诉信息卡图片，提取车辆信息和客户失效描述")
    public CompletableFuture<OcrResultDTO> recognize(
            @Parameter(description = "客诉信息卡图片文件", required = true)
            @RequestParam("file") MultipartFile file) {

        log.info("收到OCR识别请求: 文件名={}, 大小={}KB",
                file.getOriginalFilename(), file.getSize() / 1024);

        // 验证文件
        validateFile(file);

        // 模拟5-15秒的随机延迟
        int delaySeconds = 5 + random.nextInt(11);

        log.info("开始OCR识别，预计处理时间: {}秒", delaySeconds);

        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(delaySeconds * 1000L);
                OcrResultDTO result = generateMockResult();
                log.info("OCR识别完成");
                return result;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("OCR识别被中断", e);
                throw new RuntimeException("OCR识别被中断", e);
            }
        }, executor);
    }

    /**
     * 验证上传的文件
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("未提供文件");
        }

        String contentType = file.getContentType();
        if (contentType == null ||
                (!contentType.startsWith("image/jpeg") &&
                 !contentType.startsWith("image/png") &&
                 !contentType.startsWith("image/jpg"))) {
            throw new IllegalArgumentException("不支持的文件格式，请上传jpg、png或jpeg格式的图片");
        }

        final long MAX_FILE_SIZE = 10 * 1024 * 1024L;
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文件大小超过限制，最大支持10MB");
        }
    }

    /**
     * 生成模拟OCR识别结果
     */
    private OcrResultDTO generateMockResult() {
        LocalDate now = LocalDate.now();
        LocalDate productionDate = now.minusMonths(random.nextInt(12) + 6);
        LocalDate purchaseDate = productionDate.plusMonths(random.nextInt(3) + 1);
        LocalDate failureDate = now.minusDays(random.nextInt(30) + 1);

        String[] vins = {
            "LSVAB2183E2123456",
            "LSVCD4291F3456789",
            "WVWEF5382G1234567",
            "WBA3A5C50EF123456",
            "LSVAA4381E1765432",
            "LHGTD3845K2345678"
        };

        String[] descriptions = {
            "发动机异响，怠速不稳",
            "怠速抖动，加速无力",
            "传感器读数不准确",
            "电路板烧毁",
            "连接器松动导致断电",
            "空调制冷效果差",
            "转向系统异响",
            "制动系统失灵",
            "变速箱换挡顿挫",
            "电子系统故障灯亮"
        };

        // 随机决定哪些字段识别成功
        return OcrResultDTO.builder()
                .vehicleProductionDate(random.nextDouble() < 0.7 ? productionDate.format(DATE_FORMATTER) : null)
                .vehiclePurchaseDate(random.nextDouble() < 0.7 ? purchaseDate.format(DATE_FORMATTER) : null)
                .vehicleFailureDate(random.nextDouble() < 0.8 ? failureDate.format(DATE_FORMATTER) : null)
                .vehicleVIN(random.nextDouble() < 0.9 ? vins[random.nextInt(vins.length)] : null)
                .vehicleMileage(random.nextDouble() < 0.75 ? 5000 + random.nextInt(50000) : null)
                .customerDescription(random.nextDouble() < 0.8 ? descriptions[random.nextInt(descriptions.length)] : null)
                .build();
    }
}
