package com.example.github_event_capture.service.impl;

import com.example.github_event_capture.service.EventProducer;
import com.example.github_event_capture.utils.EventAccess;
import com.example.github_event_capture.entity.Event;
import org.springframework.stereotype.Service;
import org.springframework.kafka.core.KafkaTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Service
public class EventProducerImpl  {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic = "github-event-topic";
    private final ObjectMapper mapper = new ObjectMapper();
    private static final Logger LOGGER = LoggerFactory.getLogger(EventProducerImpl.class);

    public EventProducerImpl(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;

    }

    public void sendEvent(String eventType, String payload) {
        Class<?extends Event> EventClass = EventAccess.getEventObj(eventType);
        try {
            Event eventObj = mapper.readValue(payload, EventClass);
            String value = mapper.writeValueAsString(eventObj);
            kafkaTemplate.send(topic, eventType, value);
        } catch (JsonProcessingException e) {
            LOGGER.error(e.getMessage());
        }
    }
}
