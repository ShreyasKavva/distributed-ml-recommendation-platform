package com.recplatform.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Message payload published to the {@code user-activity-events} Kafka topic
 * whenever a user views, clicks, rates, or favorites a business. This is a
 * plain event DTO, not a JPA entity.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserActivityEvent {

    @NotBlank
    private String userId;

    @NotBlank
    private String businessId;

    @NotNull
    private EventType eventType;

    /** Present only when eventType == RATE. */
    private Double rating;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp;

    public enum EventType {
        VIEW, CLICK, RATE, FAVORITE
    }
}
