# DigiLocker Cloud - AWS Infrastructure Plan

## 1. Purpose

This document describes the proposed AWS services, deployment architecture, security controls, and implementation sequence for the DigiLocker Cloud project.

It is a planning document. AWS resources and failover behavior must be deployed and tested before they can be considered implemented.

## 2. Proposed AWS Services

- **Amazon Cognito:** Manages user authentication for Issuer, Citizen, and Requester roles.
- **Amazon DynamoDB:** Stores user profiles, document metadata, consent records, and audit information.
- **Amazon S3:** Stores actual documents such as PDF files in private buckets.
- **AWS KMS:** Manages encryption keys used to protect stored data.
- **AWS IAM:** Controls what AWS resources the application is permitted to access.
- **Amazon EC2:** Hosts the Java Spring Boot backend application.
- **Application Load Balancer (ALB):** Routes incoming requests to healthy backend instances.
- **EC2 Auto Scaling:** Helps maintain the required application instance capacity and replace failed instances.
- **Amazon CloudWatch:** Collects application logs and metrics and supports alarms.

## 3. Proposed Region and Availability Design

- The proposed primary AWS Region is Mumbai (`ap-south-1`), subject to team and AWS account confirmation.
- The working high-availability demonstration is planned across two Availability Zones within one AWS Region.
- The conceptual national-scale architecture may include Mumbai (`ap-south-1`) and Hyderabad (`ap-south-2`), as described in the project handover.
- Multi-region deployment must not be claimed unless it has actually been deployed and tested.

## 4. Proposed Deployment Sequence

1. Confirm AWS account access, IAM permissions, available budget, and cleanup responsibility.
2. Agree on the VPC, subnet, and network security design.
3. Prepare application configuration and secure handling of credentials.
4. Configure private Amazon S3 document storage and encryption.
5. Configure DynamoDB tables and required access permissions.
6. Configure authentication and role handling with Amazon Cognito.
7. Deploy the Spring Boot backend on two EC2 instances in different Availability Zones.
8. Register both application instances behind an Application Load Balancer.
9. Configure ALB health checks and EC2 Auto Scaling.
10. Configure CloudWatch logs, metrics, and alarms.
11. Test application availability when one EC2 instance fails.
12. Record test observations, screenshots, and cleanup steps.

## 5. Security and Cost Controls

- Use least-privilege IAM roles instead of hard-coded AWS credentials.
- Keep S3 buckets private and restrict document access.
- Use encryption for sensitive stored documents.
- Do not commit passwords, tokens, access keys, or other secrets to GitHub.
- Avoid exposing private document URLs or sensitive personal information.
- Create only the AWS resources required for the approved student demonstration.
- Review possible charges for EC2, ALB, NAT gateways, KMS, and CloudWatch before deployment.
- Remove unused AWS resources after testing.

## 6. Planned High Availability and Failover Demonstration

The proposed demonstration uses two Spring Boot application instances in separate Availability Zones.

### Step 1: Normal Operation

Both EC2 instances are running, and the Application Load Balancer checks their health.

### Step 2: Simulate Failure

Stop or terminate one application instance using the team's approved test procedure.

### Step 3: Observe Availability

Check whether the failed target becomes unhealthy and whether requests continue through the remaining healthy instance.

Some requests may fail while the load balancer detects the failure. The actual behavior must be recorded.

### Step 4: Recovery

If Auto Scaling is configured correctly, check whether it launches a replacement instance and whether the replacement becomes healthy.

### Step 5: Save Evidence

Preserve screenshots, request timestamps, target health results, and the observed outcome.

This test has not yet been performed as part of this documentation task.

## 7. Evidence Required After Implementation

The team should collect:

- Final AWS architecture diagram.
- EC2 instance details and Availability Zones.
- ALB target group health information.
- Application response through the ALB endpoint.
- CloudWatch logs, metrics, and alarms.
- Failover test steps, timestamps, and screenshots.
- Recovery and cleanup observations.

## 8. Current Implementation Status

The existing Java Spring Boot backend has been run locally using Java 21 and Maven.

Its Actuator health endpoint was successfully tested on localhost port 8081 during local development.

This does not demonstrate an AWS deployment, AWS service integration, or high availability.

All AWS resources, permissions, cloud integration, and failover behavior must be independently configured, verified, and documented by the team.
