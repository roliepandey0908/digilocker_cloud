
# DigiLocker Cloud – API Contract

## 1. Project Overview

This document defines the proposed REST API contract
for the DigiLocker-inspired document management system.

Base URL: http://localhost:8080

API Version: v1

All endpoints are proposed and subject to team approval.

## 2. User Roles

- ISSUER
- CITIZEN
- REQUESTER

## 3. Issuer APIs

### 3.1 Register Student

Method: POST

Endpoint: /api/v1/issuer/students

Purpose: Register a student under an institution.

### 3.2 Upload Document

Method: POST

Endpoint: /api/v1/issuer/documents

Purpose: Upload a document and store its metadata
and SHA-256 hash.

## 4. Citizen APIs

### 4.1 Get Linked Documents

Method: GET

Endpoint: /api/v1/citizen/documents

Purpose: Retrieve documents linked to the authenticated citizen.

### 4.2 Create Link Request

Method: POST

Endpoint: /api/v1/citizen/link-requests

Purpose: Request first-time document linking.

### 4.3 Consent Decision

Method: POST

Endpoint: /api/v1/citizen/consents/{id}/decision

Purpose: Approve or reject a document access request.

## 5. Requester APIs

### 5.1 Create Document Request

Method: POST

Endpoint: /api/v1/requests

Purpose: Create a request for a citizen's document.

Proposed request body:

{
  "citizenId": "CIT001",
  "documentType": "DEGREE_CERTIFICATE",
  "purpose": "Educational verification",
  "expiryDate": "2026-12-31"
}

### 5.2 Retrieve Approved Document

Method: GET

Endpoint: /api/v1/requests/{id}/document

Purpose: Retrieve a document only when valid consent exists.

### 5.3 Verify Document

Method: POST

Endpoint: /api/v1/documents/{id}/verify

Purpose: Verify document integrity using SHA-256 hash comparison.

## 6. Request Status

- PENDING
- APPROVED
- REJECTED
- EXPIRED
- REVOKED

## 7. Common Response Format

Success response:

{
  "success": true,
  "message": "Operation successful",
  "data": {}
}

Error response:

{
  "success": false,
  "message": "Operation failed",
  "data": null
}

## 8. Security Requirements

- Authentication is required for protected APIs.
- Role-based authorization must be enforced.
- User identity must be derived from verified authentication claims.
- Requesters must not access documents without valid consent.
- Expired and revoked grants must deny access.
- Sensitive information must not be exposed in application logs.

## 9. Document Verification

The system uses SHA-256 to calculate document hashes.

The calculated hash is compared with the trusted hash
stored in issuer document metadata.

Matching hashes indicate that document content has not
changed relative to the trusted stored hash.

SHA-256 hash comparison alone does not prove issuer identity.
