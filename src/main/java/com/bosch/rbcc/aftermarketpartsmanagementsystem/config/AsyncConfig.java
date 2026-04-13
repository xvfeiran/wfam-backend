package com.bosch.rbcc.aftermarketpartsmanagementsystem.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "importTaskExecutor", destroyMethod = "close")
    public ExecutorService importTaskExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("import-vt-", 0).factory());
    }

    @Bean(name = "ocrTaskExecutor", destroyMethod = "close")
    public Executor ocrTaskExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("ocr-vt-", 0).factory());
    }
}
