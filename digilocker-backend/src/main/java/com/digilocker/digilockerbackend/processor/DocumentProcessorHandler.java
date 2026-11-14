
package com.digilocker.digilockerbackend.processor;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.digilocker.digilockerbackend.processor.config.ProcessorAwsConfig;
import com.digilocker.digilockerbackend.processor.service.DocumentProcessingService;
import com.digilocker.digilockerbackend.processor.service.DocumentPersistenceService;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class DocumentProcessorHandler
        implements RequestHandler<SQSEvent, Void> {

    private static final System.Logger LOGGER =
            System.getLogger(DocumentProcessorHandler.class.getName());

    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper();

    private static final String INCOMING_PREFIX = "incoming/";
    private static final String PDF_SUFFIX = ".pdf";

    private static final long MAX_DOCUMENT_SIZE =
            10L * 1024 * 1024;

    private final DocumentProcessingService processingService =
            ProcessorAwsConfig.documentProcessingService();

    private final DocumentPersistenceService persistenceService =
            ProcessorAwsConfig.documentPersistenceService();

    @Override
    public Void handleRequest(SQSEvent event, Context context) {

        if (event == null || event.getRecords() == null
                || event.getRecords().isEmpty()) {

            LOGGER.log(System.Logger.Level.WARNING,
                    "Received an empty SQS event.");

            return null;
        }

        String expectedBucket = System.getenv("DOCUMENTS_BUCKET");

        if (expectedBucket == null || expectedBucket.isBlank()) {
            throw new IllegalStateException(
                    "DOCUMENTS_BUCKET environment variable is missing.");
        }

        for (SQSEvent.SQSMessage message : event.getRecords()) {

            if (message.getBody() == null || message.getBody().isBlank()) {
                throw new IllegalArgumentException(
                        "SQS message body is empty.");
            }

            try {
                JsonNode s3Event = OBJECT_MAPPER.readTree(message.getBody());

                JsonNode records = s3Event.path("Records");

                // S3 test notifications do not contain normal Records.
                if (!records.isArray() || records.isEmpty()) {
                    LOGGER.log(System.Logger.Level.WARNING,
                            "SQS message contains no S3 event records.");
                    continue;
                }

                for (JsonNode record : records) {

                    String bucketName = requiredText(
                            record.path("s3").path("bucket").path("name"),
                            "S3 bucket name");

                    String encodedKey = requiredText(
                            record.path("s3").path("object").path("key"),
                            "S3 object key");

                    String objectKey = URLDecoder.decode(
                            encodedKey,
                            StandardCharsets.UTF_8
                    );

                    String versionId = requiredText(
                            record.path("s3").path("object").path("versionId"),
                            "S3 object version ID");

                    processS3Record(
                            bucketName,
                            objectKey,
                            versionId,
                            expectedBucket
                    );
                }

            } catch (IOException exception) {
                throw new IllegalArgumentException(
                        "Unable to parse S3 notification from SQS message.",
                        exception
                );
            }
        }

        return null;
    }

    private String requiredText(JsonNode node, String fieldName) {

        if (node == null || node.isMissingNode()
                || node.isNull() || node.asText().isBlank()) {

            throw new IllegalArgumentException(
                    "S3 notification is missing " + fieldName + ".");
        }

        return node.asText();
    }


    private void processS3Record(
            String bucketName,
            String objectKey,
            String versionId,
            String expectedBucket) {

        if (!expectedBucket.equals(bucketName)) {
            throw new IllegalArgumentException(
                    "Unexpected S3 bucket: " + bucketName);
        }

        if (objectKey == null
                || !objectKey.startsWith(INCOMING_PREFIX)
                || !objectKey.toLowerCase(Locale.ROOT).endsWith(PDF_SUFFIX)) {

            LOGGER.log(System.Logger.Level.INFO,
                    "Skipping unsupported S3 object: " + objectKey);

            return;
        }

        if (versionId == null || versionId.isBlank()) {
            throw new IllegalArgumentException(
                    "S3 event does not contain an object version ID.");
        }

        try {

            LOGGER.log(System.Logger.Level.INFO,
                    "Claiming document for processing: " + objectKey);

            // STEP 1: Claim the registered S3 object version.
            String processingToken =
                    persistenceService.claimDocumentForProcessing(
                            objectKey,
                            versionId
                    );

            LOGGER.log(System.Logger.Level.INFO,
                    "Document processing claim acquired.");

            // STEP 2: Check the size of the exact S3 version.
            HeadObjectResponse headResponse =
                    ProcessorAwsConfig.s3Client().headObject(
                            HeadObjectRequest.builder()
                                    .bucket(bucketName)
                                    .key(objectKey)
                                    .versionId(versionId)
                                    .build()
                    );

            Long contentLength = headResponse.contentLength();

            if (contentLength == null
                    || contentLength <= 0
                    || contentLength > MAX_DOCUMENT_SIZE) {

                throw new IllegalArgumentException(
                        "Document is empty or exceeds the 10 MB limit.");
            }

            // STEP 3: Download the exact S3 version.
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .versionId(versionId)
                    .build();

            ResponseBytes<GetObjectResponse> response =
                    ProcessorAwsConfig.s3Client()
                            .getObjectAsBytes(request);

            byte[] documentBytes = response.asByteArray();

            if (documentBytes.length == 0
                    || documentBytes.length > MAX_DOCUMENT_SIZE) {

                throw new IllegalArgumentException(
                        "Downloaded document is empty or exceeds the 10 MB limit.");
            }

            LOGGER.log(System.Logger.Level.INFO,
                    "Document downloaded. Size: "
                            + documentBytes.length + " bytes");

            // STEP 4: Validate, hash and sign using KMS.
            DocumentProcessingService.ProcessedDocument result =
                    processingService.processDocument(documentBytes);

            LOGGER.log(System.Logger.Level.INFO,
                    "Document hash calculated and signature generated.");

            // STEP 5: Persist the issued PDF, signature and metadata.
            persistenceService.persistProcessedDocument(
                    objectKey,
                    versionId,
                    documentBytes,
                    result,
                    processingToken
            );

            LOGGER.log(System.Logger.Level.INFO,
                    "Document successfully issued: " + objectKey);

        } catch (DocumentPersistenceService.AlreadyIssuedException exception) {

            LOGGER.log(System.Logger.Level.INFO,
                    "Document is already issued; acknowledging duplicate event: "
                            + objectKey);
        }
    }

}
