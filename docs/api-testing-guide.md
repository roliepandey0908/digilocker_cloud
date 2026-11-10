
# DigiLocker Cloud - API Testing Guide

## 1. Introduction

This document defines common API testing
standards for the DigiLocker Cloud project.

## 2. Testing Tools

- Postman
- Swagger UI
- Spring Boot Test
- JUnit

## 3. Functional Testing

Test the following scenarios:

- Successful student registration.
- Successful document upload.
- Successful document request creation.
- Successful consent approval.
- Successful consent rejection.
- Successful document retrieval.
- Successful document hash verification.

## 4. Validation Testing

- Missing required fields.
- Invalid email format.
- Invalid document type.
- Invalid expiry date.
- Invalid user ID.
- Empty request body.

## 5. Authorization Testing

- Unauthenticated users must not access protected APIs.
- Issuer must not access restricted citizen operations.
- Requester must not approve their own document requests.
- Requester must not access documents without valid consent.

## 6. Consent Testing

- Pending requests must not allow document access.
- Approved requests with valid consent can allow access.
- Rejected requests must deny access.
- Expired consent must deny access.
- Revoked consent must deny access.

## 7. Document Integrity Testing

- Calculate SHA-256 hash of the uploaded document.
- Retrieve the authorized document.
- Calculate its SHA-256 hash again.
- Compare both hashes.
- Matching hashes indicate unchanged document content.

## 8. Error Handling

All APIs should follow a consistent error response format.

Example:

{
  "success": false,
  "message": "Unauthorized access",
  "data": null
}

## 9. Testing Evidence

Maintain the following evidence:

- Postman screenshots.
- Swagger API responses.
- Test execution results.
- Failed authorization test results.
- Document verification results.

## 10. Conclusion

API testing ensures correctness, security,
authorization and reliability of the application.
