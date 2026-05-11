package com.bosch.rbcc.aftermarketpartsmanagementsystem.controller.part;

import com.bosch.rbcc.aftermarketpartsmanagementsystem.dto.ImageUploadResult;
import com.bosch.rbcc.aftermarketpartsmanagementsystem.service.PartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/parts/{partId}/images")
@RequiredArgsConstructor
@Tag(name = "售后件图片", description = "售后件缺陷照片上传与管理")
public class PartImageController {

    private final PartService partService;

    @PostMapping
    @Operation(summary = "上传售后件图片", description = "上传一张缺陷照片，自动关联到指定售后件")
    public ImageUploadResult uploadImage(
            @PathVariable String partId,
            @RequestParam("file") MultipartFile file) {
        return partService.uploadImage(partId, file);
    }

    @DeleteMapping("/{imageId:.+}")
    @Operation(summary = "删除售后件图片", description = "删除指定缺陷照片")
    public void deleteImage(
            @PathVariable String partId,
            @PathVariable String imageId) {
        String relativePath = "parts/" + partId + "/" + imageId;
        partService.deleteImage(partId, relativePath);
    }
}
