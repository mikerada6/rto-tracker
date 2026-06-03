package com.rto.tracker.dto;

import com.rto.tracker.domain.CommuteAnnotation;
import com.rto.tracker.domain.CommuteAnnotationCategory;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

public class CommuteAnnotationDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateRequest {

        @NotNull(message = "startTime is required")
        private Instant startTime;

        @NotNull(message = "endTime is required")
        private Instant endTime;

        @NotNull(message = "category is required (SOCIAL, ERRAND, DINNER, PERSONAL, OTHER)")
        private CommuteAnnotationCategory category;

        @Size(max = 1000, message = "note must be at most 1000 characters")
        private String note;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateRequest {

        @NotNull(message = "category is required (SOCIAL, ERRAND, DINNER, PERSONAL, OTHER)")
        private CommuteAnnotationCategory category;

        @Size(max = 1000, message = "note must be at most 1000 characters")
        private String note;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private UUID id;
        private Instant startTime;
        private Instant endTime;
        private CommuteAnnotationCategory category;
        private String note;

        public static Response from(CommuteAnnotation ann) {
            return Response.builder()
                    .id(ann.getId())
                    .startTime(ann.getStartTime())
                    .endTime(ann.getEndTime())
                    .category(ann.getCategory())
                    .note(ann.getNote())
                    .build();
        }
    }
}
