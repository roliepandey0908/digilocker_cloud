package com.digilocker.digilockerbackend.controller;
import io.swagger.v3.oas.annotations.Hidden;
import com.digilocker.digilockerbackend.service.S3HealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@Hidden
@RestController
@RequestMapping("/api/test")
public class S3Controller {

    private final S3HealthService s3HealthService;

    public S3Controller(S3HealthService s3HealthService) {
        this.s3HealthService = s3HealthService;
    }

    @GetMapping("/s3")
    public String testS3() {
        return s3HealthService.checkConnection();
    }
}