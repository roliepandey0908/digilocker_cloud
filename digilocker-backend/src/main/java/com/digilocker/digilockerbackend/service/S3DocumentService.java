package com.digilocker.digilockerbackend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.time.Duration;

import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.IOException;

@Service
public class S3DocumentService {

    private final S3Client s3Client;

    private final S3Presigner s3Presigner;

    @Value("${app.aws.s3.bucket-name}")
    private String bucketName;

    public S3DocumentService(
            S3Client s3Client,
            S3Presigner s3Presigner) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    // Holds the S3 object key and its exact version ID.
    public record UploadedDocument(
            String s3Key,
            String versionId
    ) {}

    public String generateViewUrl(String issuedS3Key) {

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(issuedS3Key)
                .responseContentType("application/pdf")
                .responseContentDisposition("inline")
                .build();

        GetObjectPresignRequest presignRequest =
                GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(5))
                        .getObjectRequest(getObjectRequest)
                        .build();

        return s3Presigner.presignGetObject(presignRequest)
                .url()
                .toString();
    }

    public String generateDownloadUrl(String issuedS3Key) {

        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(issuedS3Key)
                .responseContentType("application/pdf")
                .responseContentDisposition("attachment")
                .build();

        GetObjectPresignRequest presignRequest =
                GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(5))
                        .getObjectRequest(request)
                        .build();

        return s3Presigner.presignGetObject(presignRequest)
                .url()
                .toString();
    }

    public UploadedDocument uploadIncomingDocument(
            MultipartFile file,
            String issuerId,
            String documentType,
            String documentId
    ) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("PDF file is required");
        }

        if (!"application/pdf".equalsIgnoreCase(file.getContentType())) {
            throw new IllegalArgumentException("Only PDF files are allowed");
        }

        if (documentType == null
                || !documentType.matches("^[A-Z0-9]+(?:-[A-Z0-9]+)*$")) {
            throw new IllegalArgumentException("Invalid document type");
        }

        if (issuerId == null || !issuerId.matches("^[A-Za-z0-9_-]+$")) {
            throw new IllegalArgumentException("Invalid issuer ID");
        }

        if (documentId == null || !documentId.matches("^[A-Za-z0-9_-]+$")) {
            throw new IllegalArgumentException("Invalid document ID");
        }

        String s3Key = "incoming/"
                + issuerId + "/"
                + documentType + "/"
                + documentId + ".pdf";

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType("application/pdf")
                .build();

        // Capture S3's response after uploading.
        PutObjectResponse response = s3Client.putObject(
                request,
                RequestBody.fromInputStream(
                        file.getInputStream(),
                        file.getSize()
                )
        );

        String versionId = response.versionId();

        if (versionId == null || versionId.isBlank()) {
            throw new IllegalStateException(
                    "S3 did not return an object version ID."
            );
        }

        return new UploadedDocument(s3Key, versionId);
    }
}