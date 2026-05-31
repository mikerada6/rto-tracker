package com.rto.tracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ApiKeyResponse {
    private String apiKey;
    private String message;
}
