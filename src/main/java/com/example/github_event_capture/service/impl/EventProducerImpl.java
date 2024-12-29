package com.example.github_event_capture.service.impl;

import com.example.github_event_capture.service.EventProducer;
import org.springframework.stereotype.Service;
import org.springframework.kafka.core.KafkaTemplate;


@Service
public class EventProducerImpl<K, V> implements EventProducer<K, V> {
    private final KafkaTemplate<K, V> kafkaTemplate;
    private final String topic = "github-event-topic";

    public EventProducerImpl(KafkaTemplate<K, V> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;

    }

    public void sendEvent(K eventType, V payload) {
        kafkaTemplate.send(topic, eventType, payload);
    }
}
