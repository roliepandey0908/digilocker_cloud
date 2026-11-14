
package com.digilocker.digilockerbackend.processor.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class DocumentHashService {

    public byte[] calculateSha256(byte[] documentBytes) {

        if (documentBytes == null || documentBytes.length == 0) {
            throw new IllegalArgumentException(
                    "Document content cannot be empty.");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            return digest.digest(documentBytes);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is not available.", exception);
        }
    }

    public String calculateSha256Hex(byte[] documentBytes) {

        byte[] hash = calculateSha256(documentBytes);

        return HexFormat.of().formatHex(hash);
    }
}
