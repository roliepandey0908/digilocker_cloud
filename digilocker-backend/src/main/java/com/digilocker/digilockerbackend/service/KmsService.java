package com.digilocker.digilockerbackend.service;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.MessageType;
import software.amazon.awssdk.services.kms.model.SignRequest;
import software.amazon.awssdk.services.kms.model.SigningAlgorithmSpec;
import software.amazon.awssdk.services.kms.model.VerifyRequest;

@Service
public class KmsService {

    private static final SigningAlgorithmSpec SIGNING_ALGORITHM =
            SigningAlgorithmSpec.RSASSA_PSS_SHA_256;

    private final KmsClient kmsClient;

    @Value("${app.aws.kms.key-id}")
    private String keyId;

    public KmsService(KmsClient kmsClient) {
        this.kmsClient = kmsClient;
    }

    public byte[] signDigest(byte[] digest) {

        validateDigest(digest);

        SignRequest request = SignRequest.builder()
                .keyId(keyId)
                .message(SdkBytes.fromByteArray(digest))
                .messageType(MessageType.DIGEST)
                .signingAlgorithm(SIGNING_ALGORITHM)
                .build();

        return kmsClient.sign(request).signature().asByteArray();
    }

    public boolean verifyDigest(byte[] digest, byte[] signature) {

        validateDigest(digest);

        if (signature == null || signature.length == 0) {
            throw new IllegalArgumentException("Signature cannot be empty");
        }

        VerifyRequest request = VerifyRequest.builder()
                .keyId(keyId)
                .message(SdkBytes.fromByteArray(digest))
                .messageType(MessageType.DIGEST)
                .signature(SdkBytes.fromByteArray(signature))
                .signingAlgorithm(SIGNING_ALGORITHM)
                .build();

        return kmsClient.verify(request).signatureValid();
    }

    private void validateDigest(byte[] digest) {
        if (digest == null || digest.length != 32) {
            throw new IllegalArgumentException(
                    "Expected a 32-byte SHA-256 digest"
            );
        }
    }
}