package com.ulutman.controller;

import com.ulutman.model.enums.MediaFileType;
import com.ulutman.service.MinioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
@Tag(name = "Upload")
@SecurityRequirement(name = "Authorization")
public class UploadController {

    private final MinioService minioService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Загрузить файл",
            description = """
                    Загружает файл и возвращает presigned URL (действует 1 час) и objectKey для сохранения в БД.

                    | type | Куда вставлять |
                    |------|----------------|
                    | `PUBLISH_IMAGE` | поле `images` при создании публикации |
                    | `AD_IMAGE`      | поле `imagePath` в рекламе |
                    | `AD_RECEIPT`    | поле `paymentReceipt` в рекламе |
                    """
    )
    public ResponseEntity<UploadResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam MediaFileType type,
            Principal principal) {

        String entityId = principal != null ? principal.getName() : "general";
        String objectKey = minioService.upload(file, type, entityId);
        String presignedUrl = minioService.presign(objectKey);

        return ResponseEntity.ok(UploadResponse.builder()
                .url(presignedUrl)
                .objectKey(objectKey)
                .build());
    }

    @Data
    @Builder
    static class UploadResponse {
        private String url;
        private String objectKey;
    }
}
