package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

public interface FileStorageService {

    String store(String category, MultipartFile file);

    String store(String category, String subPath, MultipartFile file);

    Resource load(String category, String relativePath);

    boolean delete(String category, String relativePath);

    Path resolveFullPath(String relativePath);
}
