# DigiLocker Cloud - Database Design

## 1. Purpose

This document describes the proposed database entities and how the DigiLocker Cloud project will store information.

## 2. Storage Approach

Our project uses two main AWS storage services:

- **Amazon DynamoDB:** Stores structured application information such as users, documents, and consent records.
- **Amazon S3:** Stores actual PDF files and other document objects.

We will store the S3 object key and document metadata in DynamoDB, not the PDF file itself.

Stored documents should be encrypted, and access should be controlled using least-privilege permissions.

## 3. Proposed Database Entities

### UserProfile

Stores information about the users of our application.

Attributes:
- userId
- name
- email
- role (ISSUER, CITIZEN, REQUESTER)
- createdAt
- status

### Issuer

Stores information about institutions that issue documents.

Attributes:
- issuerId
- institutionName
- issuerType
- status

### Document

Stores information about documents issued to citizens.

Attributes:
- documentId
- issuerId
- citizenId
- documentType
- s3ObjectKey
- sha256Hash
- issuedAt
- status

### ConsentGrant

Stores permissions given by citizens to requesters.

Attributes:
- grantId
- requesterId
- citizenId
- documentId
- purpose
- status
- createdAt
- expiresAt
- revokedAt

### AuditEvent

Records important actions performed in the application.

Attributes:
- eventId
- actorId
- action
- resourceId
- timestamp
- outcome

## 4. Relationships and Access

- One issuer can issue multiple documents.
- One citizen can have multiple linked documents.
- One requester can create multiple document requests.
- A consent grant controls access to a specific document.
- Expired or revoked consent must not allow document access.
- Audit events record important actions performed by users.

## 5. DynamoDB Design to Finalize

The exact DynamoDB table structure, partition keys, sort keys, and secondary indexes will be decided based on the application's actual data access requirements.

These decisions must be reviewed by the team.

This document describes the proposed design. It does not mean that the database has already been deployed.

## 6. Security

- Use least-privilege AWS IAM permissions.
- Keep S3 document storage private.
- Encrypt stored documents.
- Do not expose private document URLs.
- Enforce user roles and document-sharing permissions in the backend.