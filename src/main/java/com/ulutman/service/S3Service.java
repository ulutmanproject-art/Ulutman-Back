package com.ulutman.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class S3Service {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.region}")
    private String awsRegion;

    public S3Service(@Value("${aws.s3.access-key-id}") String accessKeyId,
                     @Value("${aws.s3.secret-access-key}") String secretAccessKey,
                     @Value("${aws.s3.region}") String region) {

        AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);

        this.s3Client = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(awsCredentials))
                .build();
    }

    public List<String> uploadFiles(Map<String, Path> files) {
        List<String> fileUrls = new ArrayList<>();

        for (Map.Entry<String, Path> entry : files.entrySet()) {
            String originalFileName = entry.getKey();
            Path filePath = entry.getValue();

            String fileName = System.currentTimeMillis() + "_" + originalFileName;

            try {
                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(fileName)
                        .build();

                s3Client.putObject(putObjectRequest, filePath);
                fileUrls.add(getFileUrl(fileName));

            } catch (S3Exception e) {
                throw new RuntimeException("Ошибка при загрузке файла " + originalFileName + ": " + e.awsErrorDetails().errorMessage());
            }
        }

        return fileUrls;
    }

    public String getFileUrl(String fileName) {
        return "https://" + bucketName + ".s3." + awsRegion + ".amazonaws.com/" + fileName;
    }
}