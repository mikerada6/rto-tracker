package com.rto.tracker.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class VersionController {

    @Value("${app.git-commit:dev}")
    private String gitCommit;

    @GetMapping("/api/v1/version")
    public Map<String, String> getVersion() {
        return Map.of("commit", gitCommit);
    }
}
