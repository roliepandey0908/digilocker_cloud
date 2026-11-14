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

## API Endpoints
- GET /api/requester/issuers
- GET /api/requester/issuers/{issuerId}/document-types
- GET /api/requester/requests
- POST /api/requester/requests
- POST /api/requester/requests/{requestId}/verify