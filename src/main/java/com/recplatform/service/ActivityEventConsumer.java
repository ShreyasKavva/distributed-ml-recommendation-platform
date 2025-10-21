package com.recplatform.service;

import com.recplatform.model.UserActivityEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityEventConsumer {

    private final RealtimePopularityService realtimePopularityService;

    private static final double VIEW_WEIGHT = 1.0;
    private static final double CLICK_WEIGHT = 3.0;
    private static final double RATE_WEIGHT = 5.0;
    private static final double FAVORITE_WEIGHT = 4.0;

    @KafkaListener(topics = "${app.kafka.topics.user-activity}", containerFactory = "kafkaListenerContainerFactory")
    public void onActivityEvent(UserActivityEvent event) {
        double weight = switch (event.getEventType()) {
            case VIEW -> VIEW_WEIGHT;
            case CLICK -> CLICK_WEIGHT;
            case RATE -> RATE_WEIGHT;
            case FAVORITE -> FAVORITE_WEIGHT;
        };
        realtimePopularityService.recordActivity(event.getBusinessId(), weight);
        log.debug("Processed {} event for business {}", event.getEventType(), event.getBusinessId());
    }
}
