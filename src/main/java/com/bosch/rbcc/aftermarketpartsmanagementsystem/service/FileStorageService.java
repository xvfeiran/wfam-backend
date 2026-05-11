package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Slf4j
@Service
public class FileStorageService {

    @Value("${file.upload.base-path:${user.home}/.wfam/upload}")
    private String basePath;

    /**
     * Store file under {basePath}/{category}/{uuid}.{ext}.
     * Returns relative path like "ocr/abc123.jpg".
     */
    public String store(String category, MultipartFile file) {
        String ext = getExtension(file.getOriginalFilename());
        String fileName = UUID.randomUUID() + ext;
        return doStore(category, fileName, file);
    }

    /**
     * Store file under {basePath}/{category}/{subPath}.
     * subPath can include directories, e.g. "partId/uuid.jpg".
     * Returns relative path like "parts/{partId}/abc123.jpg".
     */
    public String store(String category, String subPath, MultipartFile file) {
        return doStore(category, subPath, file);
    }

    public Resource load(String category, String relativePath) {
        Path filePath = resolvePath(category, relativePath);
        if (!Files.exists(filePath)) {
            return null;
        }
        return new FileSystemResource(filePath);
    }

    public boolean delete(String category, String relativePath) {
        try {
            Path filePath = resolvePath(category, relativePath);
            return Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.error("删除文件失败: category={}, path={}", category, relativePath, e);
            return false;
        }
    }

    public Path resolvePath(String category, String relativePath) {
        return Path.of(basePath, category, relativePath);
    }

    /** Resolve a full relative path (includes category prefix) against basePath */
    public Path resolveFullPath(String relativePath) {
        return Path.of(basePath, relativePath);
    }

    public String getAbsolutePath(String category, String relativePath) {
        return resolvePath(category, relativePath).toAbsolutePath().toString();
    }

    /**
     * Move a file from one location to another under basePath.
     * @param fromRelativePath e.g. "pending/uuid.jpg"
     * @param toRelativePath   e.g. "parts/{partId}/uuid.jpg"
     * @return the toRelativePath
     */
    public String move(String fromRelativePath, String toRelativePath) {
        try {
            Path source = resolveFullPath(fromRelativePath);
            Path target = resolveFullPath(toRelativePath);
            if (!Files.exists(source)) {
                log.warn("移动文件失败，源文件不存在: {}", fromRelativePath);
                return toRelativePath;
            }
            Files.createDirectories(target.getParent());
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("文件已移动: {} -> {}", fromRelativePath, toRelativePath);
            return toRelativePath;
        } catch (IOException e) {
            log.error("移动文件失败: {} -> {}", fromRelativePath, toRelativePath, e);
            return toRelativePath;
        }
    }

    private String doStore(String category, String fileName, MultipartFile file) {
        try {
            Path targetDir = Path.of(basePath, category);
            Files.createDirectories(targetDir);
            Path targetFile = targetDir.resolve(fileName);
            Files.createDirectories(targetFile.getParent());
            Files.copy(file.getInputStream(), targetFile, StandardCopyOption.REPLACE_EXISTING);
            String relativePath = category + "/" + fileName;
            log.info("文件已存储: {}", relativePath);
            return relativePath;
        } catch (IOException e) {
            throw new ResponseStatusException(
                org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "文件保存失败", e);
        }
    }

    private String getExtension(String filename) {
        if (filename == null) return ".bin";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : ".bin";
    }
}
