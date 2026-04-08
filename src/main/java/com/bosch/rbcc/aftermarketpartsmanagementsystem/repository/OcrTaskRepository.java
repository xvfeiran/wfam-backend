package com.bosch.rbcc.aftermarketpartsmanagementsystem.repository;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.entity.OcrTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OcrTaskRepository extends JpaRepository<OcrTask, String> {

    List<OcrTask> findByPartIdOrderByCreatedAtDesc(String partId);
}
