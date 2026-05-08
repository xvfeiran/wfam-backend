package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.OcrResultDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.OcrTaskDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.OcrTask;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.Part;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.OcrTaskRepository;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.PartRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OcrService {

    public record OcrImagePayload(byte[] content, String contentType) {}

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final OcrTaskRepository ocrTaskRepo;
    private final PartRepository partRepo;
    private final OcrAsyncProcessor asyncProcessor;
    private final ObjectMapper objectMapper;

    /** 事务提交后触发异步 OCR 处理 */
    private class AfterCommitCallback implements TransactionSynchronization {
        private final String taskId;

        AfterCommitCallback(String taskId) { this.taskId = taskId; }

        @Override
        public void afterCommit() { asyncProcessor.process(taskId); }
    }

    /**
     * 创建 OCR 任务：保存文件到临时目录，立即返回 taskId，后台异步识别。
     *
     * @param file   上传的图片文件
     * @param partId 关联的 Part ID（编辑模式立即绑定；新建模式为 null，等 Part 创建后再绑定）
     */
    @Transactional
    public OcrTaskDTO createTask(MultipartFile file, String partId) {
        validateFile(file);

        String taskId   = UUID.randomUUID().toString();
        String filePath = saveToTempDir(taskId, file);

        OcrTask task = OcrTask.builder()
                .id(taskId)
                .partId(partId)
                .filePath(filePath)
                .status(OcrTask.STATUS_CREATED)
                .build();
        ocrTaskRepo.save(task);

        log.info("OCR 任务已创建: taskId={}, partId={}", taskId, partId);

        // 在事务提交后再调用异步处理，确保任务已持久化
        TransactionSynchronizationManager.registerSynchronization(new AfterCommitCallback(taskId));

        return toDTO(task, null);
    }

    /**
     * 查询任务状态（前端每 3s 轮询）。
     */
    @Transactional(readOnly = true)
    public OcrTaskDTO getTask(String taskId) {
        OcrTask task = ocrTaskRepo.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "OCR 任务不存在: " + taskId));

        OcrResultDTO result = null;
        if (OcrTask.STATUS_SUCCESS.equals(task.getStatus()) && task.getResultJson() != null) {
            try {
                result = objectMapper.readValue(task.getResultJson(), OcrResultDTO.class);
            } catch (Exception e) {
                log.error("解析 OCR 结果 JSON 失败: taskId={}", taskId, e);
            }
        }
        return toDTO(task, result);
    }

    @Transactional(readOnly = true)
    public OcrTaskDTO getLatestTaskByPartId(String partId) {
        OcrTask task = ocrTaskRepo.findFirstByPartIdAndFilePathIsNotNullOrderByCreatedAtDesc(partId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到 OCR 图片: partId=" + partId));

        OcrResultDTO result = null;
        if (OcrTask.STATUS_SUCCESS.equals(task.getStatus()) && task.getResultJson() != null) {
            try {
                result = objectMapper.readValue(task.getResultJson(), OcrResultDTO.class);
            } catch (Exception e) {
                log.error("解析 OCR 结果 JSON 失败: taskId={}", task.getId(), e);
            }
        }
        return toDTO(task, result);
    }

    @Transactional(readOnly = true)
    public OcrImagePayload getTaskImage(String taskId) {
        OcrTask task = ocrTaskRepo.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "OCR 任务不存在: " + taskId));

        if (task.getFilePath() == null || task.getFilePath().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "OCR 图片不存在: taskId=" + taskId);
        }

        try {
            Path imagePath = Path.of(task.getFilePath());
            if (!Files.exists(imagePath)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "OCR 图片文件不存在: taskId=" + taskId);
            }

            byte[] bytes = Files.readAllBytes(imagePath);
            String contentType = Files.probeContentType(imagePath);
            if (contentType == null || contentType.isBlank()) {
                contentType = inferContentTypeFromPath(imagePath);
            }
            return new OcrImagePayload(bytes, contentType);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "读取 OCR 图片失败", e);
        }
    }

    @Transactional
    public OcrTaskDTO retryTask(String taskId) {
        OcrTask task = ocrTaskRepo.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "OCR 任务不存在: " + taskId));

        if (task.getFilePath() == null || task.getFilePath().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OCR 任务缺少原图，无法重试");
        }

        Path imagePath = Path.of(task.getFilePath());
        if (!Files.exists(imagePath)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "OCR 原图不存在，无法重试");
        }

        if (OcrTask.STATUS_PROCESSING.equals(task.getStatus()) || OcrTask.STATUS_CREATED.equals(task.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "OCR 任务正在处理中，请稍后重试");
        }

        task.setStatus(OcrTask.STATUS_CREATED);
        task.setResultJson(null);
        task.setErrorMessage(null);
        ocrTaskRepo.save(task);

        // 在事务提交后再调用异步处理，确保任务状态已持久化
        TransactionSynchronizationManager.registerSynchronization(new AfterCommitCallback(taskId));

        return toDTO(task, null);
    }

    /**
     * Part 创建后将 OCR 任务与 Part 绑定。
     * <ul>
     *   <li>更新 OcrTask.partId</li>
     *   <li>若任务已成功完成，立即将识别结果写入 Part 表</li>
     *   <li>若任务仍在处理中，等异步识别完成后由 OcrAsyncProcessor 自行写入</li>
     * </ul>
     */
    @Transactional
    public void bindTaskToPart(String taskId, String partId) {
        ocrTaskRepo.findById(taskId).ifPresentOrElse(task -> {
            task.setPartId(partId);
            ocrTaskRepo.save(task);
            log.info("OCR 任务已绑定 Part: taskId={}, partId={}", taskId, partId);

            // 任务已完成 → 立即写入 Part
            if (OcrTask.STATUS_SUCCESS.equals(task.getStatus()) && task.getResultJson() != null) {
                writeOcrResultToPart(task.getResultJson(), partId);
            }
        }, () -> log.warn("bindTaskToPart: OCR 任务不存在, taskId={}", taskId));
    }

    /**
     * 将 JSON 格式的 OCR 结果写入指定 Part（供 OcrAsyncProcessor 复用）。
     * null 字段不覆盖原有值。
     */
    @Transactional
    public void writeOcrResultToPart(String resultJson, String partId) {
        try {
            OcrResultDTO result = objectMapper.readValue(resultJson, OcrResultDTO.class);
            partRepo.findById(partId).ifPresentOrElse(part -> {
                applyOcrToPart(part, result);
                partRepo.save(part);
                log.info("OCR 结果已写入 Part: partId={}", partId);
            }, () -> log.warn("writeOcrResultToPart: Part 不存在, partId={}", partId));
        } catch (Exception e) {
            log.error("OCR 结果写入 Part 失败: partId={}", partId, e);
        }
    }

    public void applyOcrToPart(Part part, OcrResultDTO result) {
        if (result.getVehicleProductionDate() != null) {
            part.setVehicleProductionDate(LocalDate.parse(result.getVehicleProductionDate(), DATE_FMT));
        }
        if (result.getVehiclePurchaseDate() != null) {
            part.setVehiclePurchaseDate(LocalDate.parse(result.getVehiclePurchaseDate(), DATE_FMT));
        }
        if (result.getVehicleFailureDate() != null) {
            part.setVehicleFailureDate(LocalDate.parse(result.getVehicleFailureDate(), DATE_FMT));
        }
        if (result.getVehicleVIN() != null) {
            part.setVehicleVin(result.getVehicleVIN());
        }
        if (result.getVehicleMileage() != null) {
            part.setVehicleMileage(result.getVehicleMileage());
        }
        if (result.getCustomerDescription() != null) {
            part.setCustomerDescription(result.getCustomerDescription());
        }
        if (result.getRepairStationLocation() != null) {
            part.setRepairStationLocation(result.getRepairStationLocation());
        }
    }

    // ── 私有方法 ──────────────────────────────────────────────────

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未提供文件");
        }
        String contentType = file.getContentType();
        if (contentType == null ||
                (!contentType.startsWith("image/jpeg") &&
                 !contentType.startsWith("image/png") &&
                 !contentType.startsWith("image/jpg"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "不支持的文件格式，请上传 jpg 或 png 格式的图片");
        }
        if (file.getSize() > 10 * 1024 * 1024L) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "文件大小超过限制，最大支持 10MB");
        }
    }

    private String saveToTempDir(String taskId, MultipartFile file) {
        try {
            String ext = getExtension(file.getOriginalFilename());
            Path tempFile = Path.of(System.getProperty("java.io.tmpdir"), "ocr_" + taskId + ext);
            Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);
            log.info("OCR 图片已保存至临时目录: {}", tempFile);
            return tempFile.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "文件保存失败", e);
        }
    }

    private String getExtension(String filename) {
        if (filename == null) return ".jpg";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : ".jpg";
    }

    private OcrTaskDTO toDTO(OcrTask task, OcrResultDTO result) {
        return OcrTaskDTO.builder()
                .taskId(task.getId())
                .partId(task.getPartId())
                .status(task.getStatus())
                .result(result)
                .errorMessage(task.getErrorMessage())
                .createdAt(task.getCreatedAt())
                .build();
    }

    private String inferContentTypeFromPath(Path imagePath) {
        String fileName = imagePath.getFileName() != null ? imagePath.getFileName().toString().toLowerCase(Locale.ROOT) : "";
        if (fileName.endsWith(".png")) {
            return "image/png";
        }
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return "application/octet-stream";
    }
}
