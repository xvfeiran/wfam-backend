package com.bosch.rbcc.aftermarketpartsmanagementsystem.repository;

import java.time.LocalDateTime;

public interface ImportRecordListView {
    String getId();
    String getImportType();
    String getFileName();
    String getStatus();
    Integer getTotalCount();
    Integer getSuccessCount();
    Integer getFailCount();
    String getCreatedBy();
    LocalDateTime getCreatedAt();
}
