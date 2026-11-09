\# Requester Module – Implementation Plan



\## 1. Introduction



The Requester module is responsible for requesting, accessing and verifying documents belonging to Citizens.



A Requester must receive permission from the Citizen before accessing their documents.



\## 2. Main Responsibilities



The Requester module will include:



\- Requester dashboard.

\- Creating document access requests.

\- Selecting the Citizen and document type.

\- Providing the purpose of the request.

\- Setting request expiry.

\- Tracking request status.

\- Accessing documents after Citizen approval.

\- Verifying document integrity using SHA-256 hashing.



\## 3. Proposed API Endpoints



| Method | API Endpoint | Purpose |

|---|---|---|

| POST | /api/v1/requests | Create a document request |

| GET | /api/v1/requests/{id}/document | Access an approved document |

| POST | /api/v1/documents/{id}/verify | Verify document integrity |



These are proposed endpoints from the team handover and will be finalized during implementation.



\## 4. AWS Services



The Requester module will use the following AWS services through the shared backend:



\- Amazon Cognito – User authentication.

\- Amazon DynamoDB – Request and consent metadata.

\- Amazon S3 – Document storage.

\- AWS KMS – Encryption key management.

\- Amazon CloudWatch – Application monitoring.



\## 5. Security



\- Only authenticated Requesters should create requests.

\- Document access requires valid Citizen consent.

\- Expired or revoked permissions must not allow document access.

\- Sensitive information should not be exposed in application logs.



\## 6. Implementation Roadmap



1\. Understand the existing Spring Boot backend.

2\. Create the Requester package structure.

3\. Implement document request creation.

4\. Implement request status tracking.

5\. Implement consent-based document retrieval.

6\. Implement SHA-256 verification.

7\. Integrate the required AWS services.

8\. Add frontend functionality.

9\. Test the complete Requester journey.



\## 7. Expected Outcome



The Requester should be able to create document requests, track their status, access approved documents and verify document integrity.

