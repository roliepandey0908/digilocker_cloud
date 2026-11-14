# Backend Structure

The backend is one Spring Boot application shared by all dashboards. Feature ownership can be divided among teammates later without creating separate backend applications.

## Package responsibilities

- `api`: HTTP controllers, request validation, response mapping, and error handling.
- `api.dto`: JSON and multipart request/response shapes. Never expose internal storage references here.
- `application`: use cases and orchestration. This layer enforces ownership and consent rules.
- `application.port.in`: operations the API can call.
- `application.port.out`: storage and repository interfaces required by the use cases.
- `domain.model`: core records and state enums, with no AWS dependencies.
- `infrastructure.persistence.local`: future in-memory or local database adapters.
- `infrastructure.storage.local`: future local PDF storage adapter.
- `infrastructure.aws`: future DynamoDB, S3, KMS, and Cognito adapters.
- `security`: authenticated identity and role checks.
- `config`: Spring configuration and environment settings.
- `common`: shared errors, IDs, and pagination helpers.

## Current scaffold boundary

The domain records, API DTOs, application ports, and OpenAPI contract define the shared structure. Concrete controllers and adapters should be implemented in small vertical slices. AWS client configuration already present in the starter is not called by these new interfaces.

## Local-first implementation order

1. Implement local repository and file-storage adapters.
2. Implement issuer document upload and citizen document listing.
3. Implement requester verification requests and citizen consent decisions.
4. Enforce ownership, expiry, and revocation on every document read.
5. Add Cognito JWT verification and AWS adapters after the local behavior is stable.


## Package layout

- api/controller exposes REST endpoints; api/dto/request and api/dto/response hold HTTP payloads.
- domain/model holds core records and enums, independent of AWS.
- application/port/in defines use cases; application/port/out defines repository/storage interfaces. These are not JPA repositories.
- infrastructure/persistence/local and infrastructure/storage/local are local adapter homes.
- infrastructure/aws/config and infrastructure/aws/{s3,dynamodb,kms,cognito} group AWS client setup and future adapters.
- config remains for general Spring configuration; keep AWS-specific configuration under infrastructure/aws/config.
- DynamoDB adapters should implement the application repository interfaces with AWS SDK v2 / Enhanced Client; do not add JpaRepository for the DynamoDB design.

