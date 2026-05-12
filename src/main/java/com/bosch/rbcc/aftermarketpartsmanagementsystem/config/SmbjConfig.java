package com.bosch.rbcc.aftermarketpartsmanagementsystem.config;

import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "custom.smb.enabled", havingValue = "true")
public class SmbjConfig {

    @Value("${custom.smb.user}")
    private String user;

    @Value("${custom.smb.password}")
    private String password;

    @Value("${custom.smb.domain}")
    private String domain;

    @Value("${custom.smb.host}")
    private String host;

    @Value("${custom.smb.share-name}")
    private String shareName;

    @Bean
    public GenericObjectPool<DiskShare> diskSharePool() {
        GenericObjectPoolConfig<DiskShare> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(10);
        poolConfig.setMinIdle(0);
        poolConfig.setMaxIdle(5);
        poolConfig.setMaxWait(Duration.ofMillis(30000));
        poolConfig.setTestOnBorrow(true);
        poolConfig.setSoftMinEvictableIdleDuration(Duration.ofMillis(60000));
        poolConfig.setTimeBetweenEvictionRuns(Duration.ofMillis(30000));
        return new GenericObjectPool<>(new DiskShareFactory(), poolConfig);
    }

    @PreDestroy
    public void closeSmbClient() {
        if (diskSharePool() != null) {
            diskSharePool().close();
        }
    }

    private class DiskShareFactory implements PooledObjectFactory<DiskShare> {

        @Override
        public PooledObject<DiskShare> makeObject() throws Exception {
            SMBClient smbClient = new SMBClient(SmbConfig.builder().withEncryptData(true).build());
            Connection connection = smbClient.connect(host);
            AuthenticationContext ac = new AuthenticationContext(user, password.toCharArray(), domain);
            Session session = connection.authenticate(ac);
            DiskShare diskShare = (DiskShare) session.connectShare(shareName);
            log.info("SMB DiskShare created: {}//{}/{}", host, shareName, domain);
            return new DefaultPooledObject<>(diskShare);
        }

        @Override
        public void destroyObject(PooledObject<DiskShare> p) throws Exception {
            DiskShare diskShare = p.getObject();
            if (diskShare != null) {
                diskShare.close();
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
        public void activateObject(PooledObject<DiskShare> p) {}

        @Override
        public void passivateObject(PooledObject<DiskShare> p) {}
    }
}
