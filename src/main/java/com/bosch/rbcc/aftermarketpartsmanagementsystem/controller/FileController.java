package com.bosch.rbcc.aftermarketpartsmanagementsystem.controller;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Tag(name = "File Access", description = "统一文件访问接口")
public class FileController {

    private static final String OCTET_STREAM = "application/octet-stream";

    private final FileStorageService fileStorageService;

    @GetMapping("/{category}/**")
    @Operation(summary = "获取存储文件", description = "根据 category 和路径获取上传的文件")
    public ResponseEntity<Resource> getFile(
            @PathVariable String category,
            HttpServletRequest request) {

        String fullPath = request.getRequestURI().substring(
            request.getRequestURI().indexOf("/api/v1/files/") + "/api/v1/files/".length());
        int slashIdx = fullPath.indexOf('/');
        String relativePath = slashIdx >= 0 ? fullPath.substring(slashIdx + 1) : "";

        if (relativePath.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件路径不能为空");
        }

        Resource resource = fileStorageService.load(category, relativePath);
        if (resource == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在: " + category + "/" + relativePath);
        }

        String contentType = inferContentType(relativePath);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    private String inferContentType(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (lower.endsWith(".xls")) return "application/vnd.ms-excel";
        if (lower.endsWith(".pdf")) return "application/pdf";
        return OCTET_STREAM;
    }
}
