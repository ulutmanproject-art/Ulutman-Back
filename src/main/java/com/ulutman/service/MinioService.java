package com.ulutman.service;

import com.ulutman.model.enums.MediaFileType;
import io.minio.*;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    @PostConstruct
    public void init() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created MinIO bucket: {}", bucket);
            } else {
                log.info("MinIO bucket '{}' is ready", bucket);
            }
        } catch (Exception e) {
            log.error("MinIO init failed: {}", e.getMessage());
        }
    }

    public String upload(MultipartFile file, MediaFileType type, String entityId) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("File is empty.");

        String ext = getExtension(file.getOriginalFilename());
        String objectKey = buildKey(type, entityId, ext);

        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
            log.info("Uploaded to MinIO: {}", objectKey);
        } catch (Exception e) {
            log.error("MinIO upload failed for key {}: {}", objectKey, e.getMessage(), e);
            throw new RuntimeException("File upload failed: " + e.getMessage());
        }

        return objectKey;
    }

    public String presign(String keyOrUrl) {
        if (keyOrUrl == null || keyOrUrl.isBlank()) return null;
        String objectKey = extractObjectKey(keyOrUrl);
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(1, TimeUnit.HOURS)
                            .build());
        } catch (Exception e) {
            log.error("Failed to presign URL for {}: {}", objectKey, e.getMessage());
            return null;
        }
    }

    public void delete(String urlOrKey) {
        String objectKey = extractObjectKey(urlOrKey);
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
            log.info("Deleted from MinIO: {}", objectKey);
        } catch (Exception e) {
            log.warn("MinIO delete failed (maybe already gone): {}", objectKey);
        }
    }



    private String buildKey(MediaFileType type, String entityId, String ext) {
        String folder = switch (type) {
            case PUBLISH_IMAGE -> "publish";
            case AD_IMAGE      -> "ads/images/" + entityId;
            case AD_RECEIPT    -> "ads/receipts/" + entityId;
        };
        return folder + "/" + UUID.randomUUID() + ext;
    }

    public String extractObjectKey(String urlOrKey) {
        if (urlOrKey == null || urlOrKey.isBlank()) {
            return null;
        }

        String clean = urlOrKey.contains("?")
                ? urlOrKey.substring(0, urlOrKey.indexOf('?'))
                : urlOrKey;

        // Если уже objectKey
        if (clean.startsWith("publish/")) {
            return clean;
        }

        // Ищем папку publish/
        int idx = clean.indexOf("publish/");
        if (idx >= 0) {
            return clean.substring(idx);
        }

        return clean;
    }

//      private String extractObjectKey(String urlOrKey) {
//        String clean = urlOrKey.contains("?") ? urlOrKey.substring(0, urlOrKey.indexOf('?')) : urlOrKey;
//        String marker = "/" + bucket + "/";
//        int idx = clean.indexOf(marker);
//        return idx >= 0 ? clean.substring(idx + marker.length()) : clean;
//    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return ".bin";
        return filename.substring(filename.lastIndexOf("."));
    }
}
