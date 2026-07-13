package com.bosch.rbcc.aftermarketpartsmanagementsystem.config;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.SmbConfigurationDTO;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SMB 连接池工厂。
 * 不再持有连接参数，改由 SmbConfigurationService 在需要时传入参数并调用 buildPool。
 */
@Slf4j
@Component
public class SmbjConfig {

    /**
     * 根据配置 DTO 构建一个新的 DiskShare 连接池。
     * 注意：池的创建本身不会立即建立 SMB 连接，连接在首次 borrowObject 时建立。
     */
    public GenericObjectPool<DiskShare> buildPool(SmbConfigurationDTO cfg) {
        GenericObjectPoolConfig<DiskShare> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(10);
        poolConfig.setMinIdle(0);
        poolConfig.setMaxIdle(5);
        poolConfig.setMaxWait(Duration.ofMillis(30000));
        poolConfig.setTestOnBorrow(true);
        // 主动探测并清理空闲连接：避免等到 smbj PacketReader 线程踩到服务端已重置的 socket
        // 才抛 Connection reset/Broken pipe。验证失败的连接会走 destroyObject 干净关闭。
        poolConfig.setTestWhileIdle(true);
        // 空闲连接在服务端 idle 超时之前就由池主动回收（干净 logoff，而非被服务端 RST）
        poolConfig.setSoftMinEvictableIdleDuration(Duration.ofMillis(30000));
        poolConfig.setTimeBetweenEvictionRuns(Duration.ofMillis(15000));
        return new GenericObjectPool<>(
                new DiskShareFactory(cfg.getHost(), cfg.getShareName(),
                        cfg.getDomain(), cfg.getUser(), cfg.getPassword()),
                poolConfig);
    }

    private static class DiskShareFactory implements PooledObjectFactory<DiskShare> {

        private final String host;
        private final String shareName;
        private final String domain;
        private final String user;
        private final String password;
        private final Map<DiskShare, SMBClient> clientMap = new ConcurrentHashMap<>();

        DiskShareFactory(String host, String shareName, String domain,
                         String user, String password) {
            this.host = host;
            this.shareName = shareName;
            this.domain = domain;
            this.user = user;
            this.password = password;
        }

        @Override
        public PooledObject<DiskShare> makeObject() throws Exception {
            SMBClient smbClient = new SMBClient(
                    SmbConfig.builder()
                            .withEncryptData(true)
                            // socket 读超时：防止连接在网络黑洞中无限挂起（如服务端静默宕机），
                            // 60s 内读不到任何数据即抛 SocketTimeoutException 并触发连接回收。
                            .withSoTimeout(60_000)
                            .build());
            Connection connection = smbClient.connect(host);
            AuthenticationContext ac = new AuthenticationContext(
                    user, password.toCharArray(), domain);
            Session session = connection.authenticate(ac);
            DiskShare diskShare = (DiskShare) session.connectShare(shareName);
            clientMap.put(diskShare, smbClient);
            log.info("SMB DiskShare created: {}//{}/{}", host, shareName, domain);
            return new DefaultPooledObject<>(diskShare);
        }

        @Override
        public void destroyObject(PooledObject<DiskShare> p) throws Exception {
            DiskShare diskShare = p.getObject();
            if (diskShare != null) {
                diskShare.close();
            }
            SMBClient client = clientMap.remove(diskShare);
            if (client != null) {
                client.close();
            }
        }

        @Override
        public boolean validateObject(PooledObject<DiskShare> p) {
            try {
                p.getObject().list("");
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public void activateObject(PooledObject<DiskShare> p) {
        }

        @Override
        public void passivateObject(PooledObject<DiskShare> p) {
        }
    }
}
