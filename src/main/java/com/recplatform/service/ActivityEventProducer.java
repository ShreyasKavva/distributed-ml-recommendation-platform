package com.recplatform.service;

import com.recplatform.model.UserActivityEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ActivityEventProducer {

    private final KafkaTemplate<String, UserActivityEvent> userActivityKafkaTemplate;

    @Value("${app.kafka.topics.user-activity}")
    private String topic;

    /** Keyed by businessId so all events for a business land on the same partition, in order. */
    public void publish(UserActivityEvent event) {
        userActivityKafkaTemplate.send(topic, event.getBusinessId(), event);
    }
}
