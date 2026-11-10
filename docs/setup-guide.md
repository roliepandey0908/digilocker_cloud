# DigiLocker Cloud - Project Setup Guide

## 1. Prerequisites

Before running the application, install:

- Git
- Java JDK 21 (required by the current backend project)
- Maven, or use the included Maven Wrapper
- Visual Studio Code or IntelliJ IDEA
- Internet access for downloading dependencies

## 2. Clone the Repository

Open PowerShell and run:

```powershell
git clone https://github.com/roliepandey0908/digilocker_cloud.git
cd digilocker_cloud
```

## 3. Select the Shared Integration Branch

```powershell
git fetch origin
git switch develop
git pull origin develop
```

If the local develop branch does not exist, use:

```powershell
git switch -c develop --track origin/develop
```

Use this alternative only if switching to develop fails.

## 4. Open the Backend

The backend is located in the `digilocker-backend` folder.

Its `pom.xml` file defines the Java version and project dependencies. The current project uses Java 21 and Spring Boot.
The project uses Java 21, the configured server port is 8080,
and the active Spring Boot profile is `local`.

The active profile selects the local development configuration
from `application-local.properties`.

## 5. Run the Backend on Windows

From the repository root:

```powershell
cd digilocker-backend
.\mvnw.cmd --version
.\mvnw.cmd spring-boot:run
```

The Maven Wrapper downloads or uses the Maven version configured for this project.

## 6. Verify the Application

Wait for a successful Spring Boot startup message.

The default local port is 8080 unless overridden by configuration.

If the health endpoint is exposed, open:

```text
http://localhost:8080/actuator/health
```

A status of `UP` indicates that the application's configured health checks pass.

During local testing, port 8080 was already occupied. The backend was successfully started on port 8081 using:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"
```

The following local endpoint was successfully tested:

```text
http://localhost:8081/actuator/health
```

The response reported `"status":"UP"`.

This confirms the local health endpoint worked. It does not confirm that every AWS integration or application feature is working.

## 7. Configuration and Secrets

- Do not commit passwords, AWS access keys, tokens, or credentials.
- Use approved environment variables or local configuration.
- Keep sensitive local files out of Git.
- Follow the team's agreed AWS configuration approach.

## 8. Troubleshooting

### Java version mismatch

Run:

```powershell
java -version
```

Check that Java 21 is being used. Review JAVA_HOME and PATH if an older version appears.

### Maven problems

Check internet connectivity, Java version, and Maven error messages.

### Port already in use

Identify which application occupies the port, or temporarily run Spring Boot on another available port.

### AWS connection problems

Check the AWS Region, credentials provider, IAM permissions, and service configuration. Never share secret credentials in screenshots.

## 9. Team Support

Share the exact command, relevant error messages, and steps to reproduce a problem.

Avoid exposing passwords, tokens, student information, or AWS secrets.

This guide documents the local setup process. AWS deployment and complete application integration must be verified separately.
