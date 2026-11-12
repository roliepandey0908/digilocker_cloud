package com.digilocker.digilockerbackend.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.digilocker.digilockerbackend.dto.auth.AuthStartResponse;

import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ChallengeNameType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.RespondToAuthChallengeRequest;

@Service
public class AuthService {

    private final CognitoIdentityProviderClient cognitoClient;

    @Value("${app.aws.cognito.client-id}")
    private String clientId;

    public AuthService(CognitoIdentityProviderClient cognitoClient) {
        this.cognitoClient = cognitoClient;
    }

    public AuthStartResponse startLogin(String email) {

        InitiateAuthRequest request = InitiateAuthRequest.builder()
                .authFlow(AuthFlowType.USER_AUTH)
                .clientId(clientId)
                .authParameters(Map.of(
                        "USERNAME", email,
                        "PREFERRED_CHALLENGE", "EMAIL_OTP"
                ))
                .build();

        var response = cognitoClient.initiateAuth(request);

        List<String> challenges = response.availableChallenges()
                .stream()
                .map(Object::toString)
                .toList();

        Map<String, String> parameters = response.challengeParameters();

        return new AuthStartResponse(
                response.challengeNameAsString(),
                response.session(),
                parameters.get("CODE_DELIVERY_DELIVERY_MEDIUM"),
                parameters.get("CODE_DELIVERY_DESTINATION"),
                challenges
        );
    }

    public AuthVerifyResult verifyOtp(
            String email,
            String otp,
            String session
    ) {

        RespondToAuthChallengeRequest request =
                RespondToAuthChallengeRequest.builder()
                        .clientId(clientId)
                        .challengeName(ChallengeNameType.EMAIL_OTP)
                        .session(session)
                        .challengeResponses(Map.of(
                                "USERNAME", email,
                                "EMAIL_OTP_CODE", otp
                        ))
                        .build();

        var response = cognitoClient.respondToAuthChallenge(request);

        var result = response.authenticationResult();

        if (result == null) {
            return new AuthVerifyResult(
                    false,
                    response.challengeNameAsString(),
                    response.session(),
                    null,
                    null,
                    null,
                    0,
                    null
            );
        }

        return new AuthVerifyResult(
                true,
                null,
                null,
                result.accessToken(),
                result.idToken(),
                result.refreshToken(),
                result.expiresIn(),
                result.tokenType()
        );
    }

    public record AuthVerifyResult(
            boolean authenticated,
            String challengeName,
            String session,
            String accessToken,
            String idToken,
            String refreshToken,
            int expiresIn,
            String tokenType
    ) {}
}