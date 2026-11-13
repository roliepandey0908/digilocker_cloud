package com.digilocker.digilockerbackend.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.digilocker.digilockerbackend.service.KmsService;
@Hidden
@RestController
@RequestMapping("/api/test")
public class KmsController {

    private final KmsService kmsService;

    public KmsController(KmsService kmsService) {
        this.kmsService = kmsService;
    }

    @GetMapping("/kms")
    public KmsTestResponse testKms() throws Exception {

        // 1. Sample document content
        String document = "DigiLocker KMS integration test - Document D001";

        // 2. Calculate SHA-256 digest
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");

        byte[] digest = sha256.digest(
                document.getBytes(StandardCharsets.UTF_8)
        );

        // 3. Sign digest using AWS KMS
        byte[] signature = kmsService.signDigest(digest);

        // 4. Verify signature using AWS KMS
        boolean verified = kmsService.verifyDigest(digest, signature);

        // 5. Return result
        return new KmsTestResponse(
                "KMS Connected Successfully!",
                HexFormat.of().formatHex(digest),
                Base64.getEncoder().encodeToString(signature),
                verified
        );
    }

    public record KmsTestResponse(
            String message,
            String sha256Digest,
            String signature,
            boolean signatureVerified
    ) {}
}