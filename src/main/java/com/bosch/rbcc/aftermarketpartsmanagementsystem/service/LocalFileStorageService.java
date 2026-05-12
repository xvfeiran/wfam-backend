package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ConditionalOnProperty(name = "custom.smb.enabled", havingValue = "false", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {

    @Value("${file.upload.base-path:${user.home}/.wfam/upload}")
    private String basePath;

    @Override
    public String store(String category, MultipartFile file) {
        String ext = getExtension(file.getOriginalFilename());
        return doStore(category, UUID.randomUUID() + ext, file);
    }

    @Override
    public String store(String category, String subPath, MultipartFile file) {
        return doStore(category, subPath, file);
    }

    @Override
    public Resource load(String category, String relativePath) {
        Path filePath = Path.of(basePath, category, relativePath);
        if (!Files.exists(filePath)) {
            return null;
        }
        return new FileSystemResource(filePath);
    }

    @Override
    public boolean delete(String category, String relativePath) {
        try {
            Path filePath = Path.of(basePath, category, relativePath);
            return Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.error("删除文件失败: category={}, path={}", category, relativePath, e);
            return false;
        }
    }

    @Override
    public Path resolveFullPath(String relativePath) {
        return Path.of(basePath, relativePath);
    }

    private String doStore(String category, String fileName, MultipartFile file) {
        try {
            Path targetDir = Path.of(basePath, category);
            Files.createDirectories(targetDir);
            Path targetFile = targetDir.resolve(fileName);
            Files.createDirectories(targetFile.getParent());
            Files.copy(file.getInputStream(), targetFile, StandardCopyOption.REPLACE_EXISTING);
            String relativePath = category + "/" + fileName;
            log.info("文件已存储(本地): {}", relativePath);
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
