
package com.digilocker.digilockerbackend.service;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DocumentIdGenerator {

    public String generateDocumentId() {
        return "DOC-" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .toUpperCase();
    }
}
