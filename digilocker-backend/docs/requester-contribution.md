# Requester Module Contribution

## Overview
Worked on the Requester workflow for the DigiLocker backend.

## Main Responsibilities
- Fetch available issuers.
- Fetch document types offered by an issuer.
- Create document access requests.
- Retrieve requests created by the requester.
- Verify consent and document authenticity.

## Backend Components
- RequesterController
- RequesterService
- RequesterVerificationResponse
- DocumentAuthenticityService
- KmsService
- S3DocumentService

## Collaboration
The Requester workflow was developed and integrated collaboratively with the team on the integration branch due to branch integration conflicts.

The completed implementation was subsequently synchronized with the individual feature/requester-module branch.

## Security
- All Requester endpoints require the REQUESTER role.
- The authenticated user's identity is obtained from the JWT token.
- Request creation and request retrieval are scoped to the authenticated requester.

## API Endpoints
- GET /api/requester/issuers
- GET /api/requester/issuers/{issuerId}/document-types
- GET /api/requester/requests
- POST /api/requester/requests
- POST /api/requester/requests/{requestId}/verify

## Requester Workflow

1. The requester retrieves the list of available issuers.
2. The requester selects an issuer and retrieves its supported document types.
3. The requester submits a document access request with the required citizen and document details.
4. The requester can retrieve the list of requests created under their identity.
5. When verifying a request, the backend checks request ownership and consent validity.
6. Document authenticity is verified using the stored document hash and digital signature.
7. A time-limited document view URL is generated after successful verification.

## Verification Checks

- Verify that the request belongs to the authenticated requester.
- Check the request status and consent expiry.
- Validate document integrity using its stored hash.
- Verify the document's digital signature.
- Limit document access through an expiry-bound view URL.