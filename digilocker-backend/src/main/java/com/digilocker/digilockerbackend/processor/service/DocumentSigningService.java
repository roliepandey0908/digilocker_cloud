
package com.digilocker.digilockerbackend.processor.service;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.SignRequest;
import software.amazon.awssdk.services.kms.model.SignResponse;
import software.amazon.awssdk.services.kms.model.MessageType;
import software.amazon.awssdk.services.kms.model.SigningAlgorithmSpec;

import java.util.Base64;

public class DocumentSigningService {

    private final KmsClient kmsClient;
    private final String kmsKeyId;

    public DocumentSigningService(KmsClient kmsClient, String kmsKeyId) {

        if (kmsClient == null) {
            throw new IllegalArgumentException("KMS client cannot be null.");
        }

        if (kmsKeyId == null || kmsKeyId.isBlank()) {
            throw new IllegalArgumentException("KMS key ID is required.");
        }

        this.kmsClient = kmsClient;
        this.kmsKeyId = kmsKeyId;
    }

    public byte[] signDigest(byte[] sha256Digest) {

        if (sha256Digest == null || sha256Digest.length != 32) {
            throw new IllegalArgumentException(
                    "A valid SHA-256 digest of 32 bytes is required.");
        }

        SignRequest signRequest = SignRequest.builder()
                .keyId(kmsKeyId)
                .message(SdkBytes.fromByteArray(sha256Digest))
                .messageType(MessageType.DIGEST)
                .signingAlgorithm(SigningAlgorithmSpec.RSASSA_PSS_SHA_256)
                .build();

        SignResponse response = kmsClient.sign(signRequest);

        return response.signature().asByteArray();
    }

    public String signDigestBase64(byte[] sha256Digest) {

        byte[] signature = signDigest(sha256Digest);

        return Base64.getEncoder().encodeToString(signature);
    }
}
