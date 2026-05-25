package com.bosch.rbcc.aftermarketpartsmanagementsystem.service;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.config.SmbjConfig;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.SmbConfigurationDTO;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.SmbConfiguration;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.repository.SmbConfigurationRepository;
import com.hierynomus.smbj.share.DiskShare;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmbConfigurationService {

    /** DB 中保持单条记录的固定主键 */
    static final String SINGLETON_ID = "smb-config-singleton";
    private static final String PASSWORD_MASK = "******";
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SmbConfigurationRepository repository;
    private final SmbjConfig smbjConfig;

    /** 当前活跃连接池（null 表示未配置或已禁用）*/
    private final AtomicReference<GenericObjectPool<DiskShare>> poolRef =
            new AtomicReference<>();

    /** 当前生效的配置（不掩码密码），供 SmbFileStorageService 读取 prefix/env */
    private volatile SmbConfigurationDTO activeConfig;

    // ── 生命周期 ──────────────────────────────────────────────────────────────

    @PostConstruct
    public void init() {
        repository.findById(SINGLETON_ID).ifPresent(cfg -> {
            activeConfig = toDTO(cfg, false);
            if (Boolean.TRUE.equals(cfg.getEnabled())) {
                try {
                    poolRef.set(smbjConfig.buildPool(activeConfig));
                    log.info("SMB连接池启动初始化成功: {}@{}/{}",
                            cfg.getSmbUser(), cfg.getHost(), cfg.getShareName());
                } catch (Exception e) {
                    log.warn("SMB连接池启动初始化失败（服务将以未配置状态运行）: {}",
                            e.getMessage());
                }
            }
        });
    }

    // ── 查询 ──────────────────────────────────────────────────────────────────

    /**
     * 返回当前配置，密码字段掩码为 ******。
     * 若未配置返回 null。
     */
    public SmbConfigurationDTO getConfiguration() {
        return repository.findById(SINGLETON_ID)
                .map(cfg -> toDTO(cfg, true))
                .orElse(null);
    }

    /** 返回当前连接池，null 表示 SMB 未配置或已禁用 */
    public GenericObjectPool<DiskShare> getPool() {
        return poolRef.get();
    }

    /** 返回当前生效配置（包含真实密码），供内部调用 toSmbPath */
    public SmbConfigurationDTO getActiveConfig() {
        return activeConfig;
    }

    // ── 写入 ──────────────────────────────────────────────────────────────────

    /**
     * 保存配置并热重载连接池。
     * 若密码字段为 ****** 则保留 DB 原密码。
     * 连接池重建失败时抛出 RuntimeException（配置已写入 DB）。
     */
    public SmbConfigurationDTO saveConfiguration(SmbConfigurationDTO dto) {
        SmbConfiguration config = repository.findById(SINGLETON_ID)
                .orElseGet(() -> SmbConfiguration.builder().id(SINGLETON_ID).build());

        config.setHost(dto.getHost());
        config.setShareName(dto.getShareName());
        config.setDomain(dto.getDomain());
        config.setSmbUser(dto.getUser());
        if (dto.getPassword() != null && !dto.getPassword().isBlank()
                && !PASSWORD_MASK.equals(dto.getPassword())) {
            config.setSmbPassword(dto.getPassword());
        }
        config.setPrefix(dto.getPrefix());
        config.setEnv(dto.getEnv());
        config.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : Boolean.TRUE);

        // repository.save 自带事务，提交后再操作连接池
        SmbConfiguration saved = repository.save(config);
        activeConfig = toDTO(saved, false);

        reloadPool(saved);

        log.info("SMB配置已保存: {}@{}/{}", saved.getSmbUser(), saved.getHost(),
                saved.getShareName());
        return toDTO(saved, true);
    }

    // ── 测试连接 ──────────────────────────────────────────────────────────────

    /**
     * 使用当前 DB 配置临时建一个连接池，借出一个连接并执行 list("")，
     * 验证后立即销毁测试池。不影响 poolRef。
     */
    public void testConnection() {
        SmbConfiguration config = repository.findById(SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "SMB配置不存在，请先保存配置"));

        SmbConfigurationDTO dto = toDTO(config, false);
        GenericObjectPool<DiskShare> testPool = smbjConfig.buildPool(dto);
        DiskShare diskShare = null;
        try {
            diskShare = testPool.borrowObject();
            diskShare.list("");
            log.info("SMB连接测试成功: {}@{}/{}", config.getSmbUser(),
                    config.getHost(), config.getShareName());
        } catch (Exception e) {
            log.error("SMB连接测试失败: {}", e.getMessage(), e);
            throw new RuntimeException("SMB连接测试失败: " + e.getMessage(), e);
        } finally {
            if (diskShare != null) {
                testPool.returnObject(diskShare);
            }
            testPool.close();
        }
    }

    // ── 私有方法 ──────────────────────────────────────────────────────────────

    private void reloadPool(SmbConfiguration config) {
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            GenericObjectPool<DiskShare> old = poolRef.getAndSet(null);
            closeQuietly(old);
            log.info("SMB已禁用，连接池已清除");
            return;
        }

        // buildPool 本身不建立连接（连接池懒初始化），因此不会在此处抛出连接错误
        GenericObjectPool<DiskShare> newPool =
                smbjConfig.buildPool(toDTO(config, false));
        GenericObjectPool<DiskShare> oldPool = poolRef.getAndSet(newPool);
        closeQuietly(oldPool);
        log.info("SMB连接池已热重载: {}@{}/{}", config.getSmbUser(),
                config.getHost(), config.getShareName());
    }

    private void closeQuietly(GenericObjectPool<DiskShare> pool) {
        if (pool != null) {
            try {
                pool.close();
            } catch (Exception e) {
                log.warn("关闭旧SMB连接池时发生错误: {}", e.getMessage());
            }
        }
    }

    private SmbConfigurationDTO toDTO(SmbConfiguration entity, boolean maskPassword) {
        return SmbConfigurationDTO.builder()
                .id(entity.getId())
                .host(entity.getHost())
                .shareName(entity.getShareName())
                .domain(entity.getDomain())
                .user(entity.getSmbUser())
                .password(maskPassword ? PASSWORD_MASK : entity.getSmbPassword())
                .prefix(entity.getPrefix())
                .env(entity.getEnv())
                .enabled(entity.getEnabled())
                .updatedAt(entity.getUpdatedAt() != null
                        ? entity.getUpdatedAt().format(FMT) : null)
                .updatedBy(entity.getUpdatedBy())
                .build();
    }
}
