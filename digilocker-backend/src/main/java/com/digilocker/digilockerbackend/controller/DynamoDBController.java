package com.digilocker.digilockerbackend.controller;
import com.digilocker.digilockerbackend.service.DynamoDBHealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Hidden;
@Hidden
@RestController
@RequestMapping("/api/test")
public class DynamoDBController {

    private final DynamoDBHealthService healthService;

    public DynamoDBController(DynamoDBHealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/dynamodb")
    public String testDynamoDB() {
        return healthService.checkConnection();
    }
}
