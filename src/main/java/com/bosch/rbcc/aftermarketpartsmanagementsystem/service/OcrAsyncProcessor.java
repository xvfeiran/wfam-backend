package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.OcrResultDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.OcrTask;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.OcrTaskRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * OCR 异步处理器 —— 独立 Bean 以保证 @Async 代理生效。
 */
@Slf4j
@Component
public class OcrAsyncProcessor {

    @Value("${ocr.dify.base-url}")
    private String difyBaseUrl;

    @Value("${ocr.dify.api-key}")
    private String difyApiKey;

    @Value("${ocr.dify.input-variable:image}")
    private String difyInputVariable;

    @Value("${ocr.dify.user:wfam-ocr}")
    private String difyUser;

    private final OcrTaskRepository ocrTaskRepo;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    // @Lazy 打破与 OcrService 的循环依赖
    @Lazy
    @Autowired
    private OcrService ocrService;

    public OcrAsyncProcessor(OcrTaskRepository ocrTaskRepo, ObjectMapper objectMapper, RestTemplate restTemplate) {
        this.ocrTaskRepo = ocrTaskRepo;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    @Async("ocrTaskExecutor")
    public void process(String taskId) {
        markProcessing(taskId);

        OcrTask task = ocrTaskRepo.findById(taskId).orElse(null);
        if (task == null) {
            log.error("OCR 任务不存在: taskId={}", taskId);
            return;
        }

        try {
            log.info("OCR 识别开始: taskId={}", taskId);
            String resultJson = callDifyOcr(task.getFilePath());
            markSuccessAndWritePart(taskId, resultJson);
            log.info("OCR 识别成功: taskId={}", taskId);
        } catch (Exception e) {
            log.error("OCR 识别失败: taskId={}", taskId, e);
            markFailed(taskId, "OCR 识别失败：" + e.getMessage());
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
            ocrService.writeOcrResultToPart(resultJson, task.getPartId());
        } else {
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

    /**
     * 调用 Dify Workflow API 完成 OCR：
     * 1. 上传图片文件到 /files/upload
     * 2. 执行 workflow（blocking 模式）
     * 3. 将 outputs 解析为 OcrResultDTO JSON
     */
    private String callDifyOcr(String filePath) throws Exception {
        byte[] imageBytes = Files.readAllBytes(Path.of(filePath));
        String fileName = Path.of(filePath).getFileName().toString();
        String mimeType = inferMimeType(fileName);

        String uploadedFileId = uploadFileToDify(imageBytes, fileName, mimeType);
        log.info("Dify 文件上传成功: fileId={}", uploadedFileId);

        JsonNode outputs = runWorkflow(uploadedFileId, mimeType);
        log.info("Dify Workflow 执行完成, outputs={}", outputs);

        OcrResultDTO result = parseOutputs(outputs);
        return objectMapper.writeValueAsString(result);
    }

    private String uploadFileToDify(byte[] imageBytes, String fileName, String mimeType) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("Authorization", "Bearer " + difyApiKey);

        ByteArrayResource fileResource = new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() { return fileName; }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileResource);
        body.add("user", difyUser);

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
        String url = difyBaseUrl + "/files/upload";

        String response = restTemplate.postForObject(url, request, String.class);
        JsonNode node = objectMapper.readTree(response);
        String fileId = node.path("id").asText(null);
        if (fileId == null || fileId.isBlank()) {
            throw new RuntimeException("Dify 文件上传响应缺少 id: " + response);
        }
        return fileId;
    }

    private JsonNode runWorkflow(String uploadedFileId, String mimeType) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + difyApiKey);

        // 根据 MIME 类型确定 Dify 文件类型
        String difyType = mimeType.startsWith("image/") ? "image" : "document";

        // photo 变量类型为 file（单个文件），直接传对象而非数组
        Map<String, Object> fileEntry = Map.of(
            "transfer_method", "local_file",
            "upload_file_id", uploadedFileId,
            "type", difyType
        );

        Map<String, Object> requestBody = Map.of(
            "inputs", Map.of(difyInputVariable, fileEntry),
            "response_mode", "blocking",
            "user", difyUser
        );

        String bodyJson = objectMapper.writeValueAsString(requestBody);
        HttpEntity<String> request = new HttpEntity<>(bodyJson, headers);
        String url = difyBaseUrl + "/workflows/run";

        String response = restTemplate.postForObject(url, request, String.class);
        JsonNode root = objectMapper.readTree(response);

        String status = root.path("data").path("status").asText("");
        if (!"succeeded".equals(status)) {
            String error = root.path("data").path("error").asText("unknown error");
            throw new RuntimeException("Dify Workflow 执行失败: status=" + status + ", error=" + error);
        }

        return root.path("data").path("outputs");
    }

    /**
     * 将 Dify workflow outputs 解析为 OcrResultDTO。
     * 工作流输出结构：outputs.result = { "车辆生产日期": "...", "车辆VIN码": "...", ... }
     */
    private OcrResultDTO parseOutputs(JsonNode outputs) {
        // 工作流 End 节点将 LLM structured_output 映射到 outputs.result
        JsonNode result = outputs.path("result");
        if (!result.isMissingNode() && result.isObject()) {
            return mapChineseFields(result);
        }

        log.warn("无法从 Dify outputs 解析 OCR 结果，返回空结果: {}", outputs);
        return OcrResultDTO.builder().build();
    }

    /**
     * 将 LLM 输出的中文字段名映射到 OcrResultDTO。
     */
    private OcrResultDTO mapChineseFields(JsonNode node) {
        OcrResultDTO.OcrResultDTOBuilder builder = OcrResultDTO.builder();

        String productionDate = textOrNull(node, "车辆生产日期");
        String purchaseDate   = textOrNull(node, "车辆购买日期");
        String failureDate    = textOrNull(node, "车辆失效日期");
        String vin            = textOrNull(node, "车辆VIN码");
        String mileageStr     = textOrNull(node, "车辆行驶里程");
        String description    = textOrNull(node, "客户失效描述");
        String repairStation  = textOrNull(node, "维修站号/投诉地");

        builder.vehicleProductionDate(productionDate);
        builder.vehiclePurchaseDate(purchaseDate);
        builder.vehicleFailureDate(failureDate);
        builder.vehicleVIN(vin);
        builder.customerDescription(description);
        builder.repairStationLocation(repairStation);

        if (mileageStr != null) {
            try {
                // 去掉单位（如 "15234km" → 15234）
                String digits = mileageStr.replaceAll("[^0-9]", "");
                if (!digits.isEmpty()) {
                    builder.vehicleMileage(Integer.parseInt(digits));
                }
            } catch (NumberFormatException e) {
                log.warn("里程解析失败: {}", mileageStr);
            }
        }

        return builder.build();
    }

    private String textOrNull(JsonNode node, String fieldName) {
        JsonNode field = node.path(fieldName);
        if (field.isMissingNode() || field.isNull()) return null;
        String text = field.asText("").trim();
        return text.isEmpty() ? null : text;
    }

    private String inferMimeType(String fileName) {
        if (fileName == null) return "image/jpeg";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        return "image/jpeg";
    }
}
