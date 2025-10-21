package com.recplatform.config;

import com.recplatform.model.UserActivityEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Producer and consumer beans are defined explicitly (rather than relying on
 * Spring Boot's generic auto-configured KafkaTemplate<Object,Object>) so the
 * event type is strongly typed end to end, and so there's exactly one
 * unambiguous KafkaTemplate/ConsumerFactory bean in the context.
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Value("${app.kafka.topics.user-activity}")
    private String userActivityTopicName;

    @Bean
    public NewTopic userActivityTopic() {
        return TopicBuilder.name(userActivityTopicName)
                .partitions(6)
                .replicas(1)
                .build();
    }

    // ---- Producer ----

    @Bean
    public ProducerFactory<String, UserActivityEvent> userActivityProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, UserActivityEvent> userActivityKafkaTemplate(
            ProducerFactory<String, UserActivityEvent> userActivityProducerFactory) {
        return new KafkaTemplate<>(userActivityProducerFactory);
    }

    // ---- Consumer ----

    @Bean
    public ConsumerFactory<String, UserActivityEvent> userActivityConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        JsonDeserializer<UserActivityEvent> deserializer = new JsonDeserializer<>(UserActivityEvent.class);
        deserializer.addTrustedPackages("com.recplatform.model");

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UserActivityEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, UserActivityEvent> userActivityConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, UserActivityEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(userActivityConsumerFactory);
        factory.setConcurrency(3);
        return factory;
    }
}
