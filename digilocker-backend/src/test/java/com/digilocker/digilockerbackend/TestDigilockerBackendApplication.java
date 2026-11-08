package com.digilocker.digilockerbackend;

import org.springframework.boot.SpringApplication;

public class TestDigilockerBackendApplication {

    public static void main(String[] args) {
        SpringApplication.from(DigilockerBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
