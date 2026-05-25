package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.SmbConfigurationDTO;
import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.mserref.NtStatus;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.mssmb2.SMBApiException;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.File;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.UUID;

/**
 * 基于 SMB 的文件存储服务。
 * 标注 @Primary，优先于 LocalFileStorageService 被注入。
 * 若 SMB 未配置/未启用，各方法抛出 503 SERVICE_UNAVAILABLE。
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class SmbFileStorageService implements FileStorageService {

    private final SmbConfigurationService smbConfigurationService;

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
        GenericObjectPool<DiskShare> pool = requirePool();
        DiskShare diskShare = null;
        try {
            diskShare = pool.borrowObject();
            String smbPath = toSmbPath(category + "/" + relativePath);
            return innerReadFile(diskShare, smbPath);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("读取SMB文件失败", e);
            return null;
        } finally {
            if (diskShare != null) {
                pool.returnObject(diskShare);
            }
        }
    }

    @Override
    public boolean delete(String category, String relativePath) {
        GenericObjectPool<DiskShare> pool = requirePool();
        DiskShare diskShare = null;
        try {
            diskShare = pool.borrowObject();
            String smbPath = toSmbPath(category + "/" + relativePath);
            diskShare.rm(smbPath);
            log.info("SMB文件已删除: {}", smbPath);
            return true;
        } catch (SMBApiException e) {
            log.warn("SMB文件删除失败(可能不存在): {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("删除SMB文件失败", e);
            return false;
        } finally {
            if (diskShare != null) {
                pool.returnObject(diskShare);
            }
        }
    }

    @Override
    public Path resolveFullPath(String relativePath) {
        return Path.of(toSmbPath(relativePath));
    }

    // ── 私有方法 ──────────────────────────────────────────────────────────────

    private GenericObjectPool<DiskShare> requirePool() {
        GenericObjectPool<DiskShare> pool = smbConfigurationService.getPool();
        if (pool == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "SMB 未配置或已禁用，请在「设置 → SMB 存储」中配置后保存");
        }
        return pool;
    }

    private String doStore(String category, String fileName, MultipartFile file) {
        GenericObjectPool<DiskShare> pool = requirePool();
        DiskShare diskShare = null;
        try {
            diskShare = pool.borrowObject();
            String smbDir = toSmbPath(category);
            ensureDirectory(diskShare, smbDir);

            String fullPath = toSmbPath(category + "/" + fileName);
            int lastSlash = fullPath.lastIndexOf('/');
            if (lastSlash > 0) {
                ensureDirectory(diskShare, fullPath.substring(0, lastSlash));
            }

            innerWriteFile(diskShare, fullPath, file);
            String relativePath = category + "/" + fileName;
            log.info("文件已存储(SMB): {}", relativePath);
            return relativePath;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("SMB文件存储失败", e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "文件保存失败(SMB)", e);
        } finally {
            if (diskShare != null) {
                pool.returnObject(diskShare);
            }
        }
    }

    private void ensureDirectory(DiskShare diskShare, String dirPath) {
        if (diskShare.folderExists(dirPath)) {
            return;
        }
        String[] parts = dirPath.split("/");
        StringBuilder current = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (current.length() > 0) current.append("/");
            current.append(part);
            String path = current.toString();
            if (!diskShare.folderExists(path)) {
                diskShare.mkdir(path);
            }
        }
    }

    private void innerWriteFile(DiskShare diskShare, String filePath,
                                MultipartFile file) throws IOException {
        try (File smbFile = diskShare.openFile(filePath,
                EnumSet.of(AccessMask.GENERIC_WRITE), null,
                SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OVERWRITE_IF, null)) {
            try (OutputStream os = smbFile.getOutputStream();
                 InputStream is = file.getInputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
            }
        } catch (SMBApiException e) {
            if (e.getStatusCode() == NtStatus.STATUS_OBJECT_NAME_COLLISION.getValue()) {
                log.warn("文件已存在: {}", filePath);
            } else {
                throw e;
            }
        }
    }

    private ByteArrayResource innerReadFile(DiskShare diskShare,
                                            String filePath) throws IOException {
        try (File smbFile = diskShare.openFile(filePath,
                EnumSet.of(AccessMask.GENERIC_READ), null,
                SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null);
             InputStream is = smbFile.getInputStream();
             ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            return new ByteArrayResource(os.toByteArray());
        }
    }

    private String toSmbPath(String relativePath) {
        SmbConfigurationDTO cfg = smbConfigurationService.getActiveConfig();
        String prefix = (cfg != null) ? cfg.getPrefix() : "wfam";
        String env = (cfg != null) ? cfg.getEnv() : "unknown";
        return prefix + "/" + env + "/" + relativePath;
    }

    private String getExtension(String filename) {
        if (filename == null) return ".bin";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : ".bin";
    }
}
