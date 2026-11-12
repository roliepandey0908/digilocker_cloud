package com.digilocker.digilockerbackend.dto.auth;

import java.util.List;

public record AuthStartResponse(

        String challengeName,
        String session,
        String deliveryMedium,
        String destination,
        List<String> availableChallenges

) {}