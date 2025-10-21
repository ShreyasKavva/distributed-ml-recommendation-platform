package com.recplatform.controller;

import com.recplatform.model.UserActivityEvent;
import com.recplatform.service.ActivityEventProducer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/activity")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityEventProducer activityEventProducer;

    @PostMapping
    public ResponseEntity<Void> recordActivity(@Valid @RequestBody UserActivityEvent event) {
        if (event.getTimestamp() == null) {
            event.setTimestamp(Instant.now());
        }
        activityEventProducer.publish(event);
        return ResponseEntity.accepted().build();
    }
}
