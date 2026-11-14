
package com.digilocker.digilockerbackend.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.digilocker.digilockerbackend.dto.request.AdminCreateCitizenRequest;
import com.digilocker.digilockerbackend.dto.request.AdminCreateIssuerRequest;
import com.digilocker.digilockerbackend.service.AdminUserService;
import com.digilocker.digilockerbackend.dto.request.AdminCreateRequesterRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminUserService adminUserService;

    public AdminController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @PostMapping("/users/citizens")
    public ResponseEntity<Map<String, String>> createCitizen(
            @Valid @RequestBody AdminCreateCitizenRequest request) {

        String cognitoSub = adminUserService.createCitizen(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of(
                        "message", "Citizen created successfully",
                        "userId", cognitoSub,
                        "role", "CITIZEN"
                ));
    }

    @PostMapping("/users/issuers")
    public ResponseEntity<Map<String, String>> createIssuer(
            @Valid @RequestBody AdminCreateIssuerRequest request) {

        String cognitoSub = adminUserService.createIssuer(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of(
                        "message", "Issuer user created successfully",
                        "userId", cognitoSub,
                        "role", "ISSUER"
                ));
    }
    @PostMapping("/users/requesters")
    public ResponseEntity<Map<String, String>> createRequester(
            @Valid @RequestBody AdminCreateRequesterRequest request) {

        String cognitoSub = adminUserService.createRequester(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of(
                        "message", "Requester created successfully",
                        "userId", cognitoSub,
                        "role", "REQUESTER"
                ));
    }
}
