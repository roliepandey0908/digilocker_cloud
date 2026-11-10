# DigiLocker Cloud - Error Handling Guide

## 1. Purpose

This document defines a proposed consistent error response format for all DigiLocker Cloud APIs.

It will help the Issuer, Citizen, and Requester modules return clear and understandable error messages.

## 2. Proposed Error Response

All API errors should follow a common structure:

```json
{
  "success": false,
  "message": "A short, safe explanation",
  "data": null
}
```

- `success`: Indicates whether the API request succeeded.
- `message`: Explains what went wrong in simple language.
- `data`: Contains no result when an error occurs.

## 3. Suggested HTTP Status Codes

- **400 Bad Request:** The user provided invalid or missing information.
- **401 Unauthorized:** The user is not authenticated or has an invalid authentication token.
- **403 Forbidden:** The user is authenticated but does not have permission.
- **404 Not Found:** The requested resource does not exist or is not accessible.
- **409 Conflict:** The request conflicts with the current resource state.
- **413 Payload Too Large:** An uploaded file exceeds the allowed size.
- **415 Unsupported Media Type:** The uploaded file type is not supported.
- **500 Internal Server Error:** An unexpected server-side problem occurred.

## 4. Common Error Handling Rules

- Use the same error response structure across all three modules.
- Validate incoming data before processing requests.
- Do not expose Java stack traces, passwords, tokens, or AWS credentials in API responses.
- Check user roles and consent permissions in the backend.
- Return clear messages without revealing sensitive information.
- Log a request or correlation ID when practical for debugging.

## 5. Example Error Responses

### Example 1: Invalid Input (HTTP 400)

```json
{
  "success": false,
  "message": "Invalid request data",
  "data": null
}
```

### Example 2: Authentication Required (HTTP 401)

```json
{
  "success": false,
  "message": "Authentication required",
  "data": null
}
```

### Example 3: Access Denied (HTTP 403)

```json
{
  "success": false,
  "message": "You are not allowed to access this resource",
  "data": null
}
```

## 6. Example for the Citizen Module

Suppose a Citizen attempts to view a document belonging to another Citizen.

The backend should deny the request rather than return the private document.

A suitable response could be:

```json
{
  "success": false,
  "message": "You are not allowed to access this resource",
  "data": null
}
```

This is a proposed example. The actual status code and response will be finalized during API implementation.

## 7. Implementation Note

These conventions are proposed and must be implemented and tested by the backend team.

Spring Boot can use a shared global exception handler, such as a class annotated with `@RestControllerAdvice`, to handle API errors consistently.

Creating this documentation does not mean that error handling has already been implemented.
