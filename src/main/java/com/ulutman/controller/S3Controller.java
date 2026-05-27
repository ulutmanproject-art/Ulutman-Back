package com.ulutman.controller;

import com.ulutman.service.S3Service;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/S3")
@Tag(name = "Amazon S3")
@SecurityRequirement(name = "Authorization")
@RequiredArgsConstructor
public class S3Controller {

    private final S3Service s3Service;

    @Operation(summary = "Загрузить файлы в AWS")
    @ApiResponse(responseCode = "200", description = "Uploads the files to AWS and returns the URLs of the uploaded files")
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<List<String>> uploadFiles(@RequestPart("files") MultipartFile[] files) {
        Map<String, Path> filesToUpload = new HashMap<>();

        try {
            for (MultipartFile file : files) {
                Path tempFilePath = Files.createTempFile("upload-", file.getOriginalFilename());
                file.transferTo(tempFilePath.toFile());
                filesToUpload.put(file.getOriginalFilename(), tempFilePath);
            }

            List<String> fileUrls = s3Service.uploadFiles(filesToUpload);
            return ResponseEntity.ok(fileUrls);

        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(List.of("Ошибка при сохранении файла: " + e.getMessage()));
        }
    }
}