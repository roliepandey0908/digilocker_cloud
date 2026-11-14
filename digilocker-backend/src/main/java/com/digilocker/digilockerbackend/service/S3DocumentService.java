
package com.digilocker.digilockerbackend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Service
public class S3DocumentService {

    private final S3Client s3Client;

    @Value("${app.aws.s3.bucket-name}")
    private String bucketName;

    public String generateViewUrl(String key) {
        return presignPdf(key, "inline");
    }

    public String generateDownloadUrl(String key) {
        return presignPdf(key, "attachment");
    }

    private String presignPdf(String key, String disposition) {
        var request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .responseContentType("application/pdf")
                .responseContentDisposition(disposition)
                .build();
        return s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(java.time.Duration.ofMinutes(5))
                .getObjectRequest(request)
                .build()).url().toString();
    }

    private final S3Presigner s3Presigner;

    public S3DocumentService(S3Client s3Client, S3Presigner s3Presigner) {
        this.s3Presigner = s3Presigner;
        this.s3Client = s3Client;
    }

    public String uploadIncomingDocument(
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

        s3Client.putObject(
                request,
                RequestBody.fromInputStream(
                        file.getInputStream(),
                        file.getSize()
                )
        );

        return s3Key;
    }
}
