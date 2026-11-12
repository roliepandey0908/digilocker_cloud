package com.digilocker.digilockerbackend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.IOException;

@Service
public class S3DocumentService {

    private final S3Client s3Client;

    @Value("${app.aws.s3.bucket-name}")
    private String bucketName;

    public S3DocumentService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    // Holds the S3 object key and its exact version ID.
    public record UploadedDocument(
            String s3Key,
            String versionId
    ) {}

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

        if (!documentType.matches("^(DEGREE|MARKSHEET)$")) {
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