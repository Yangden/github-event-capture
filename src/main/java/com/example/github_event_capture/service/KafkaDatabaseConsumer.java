package com.example.github_event_capture.service;

import com.example.github_event_capture.entity.Event;
import com.example.github_event_capture.entity.dto.IssueEventDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.github_event_capture.utils.EventAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.github_event_capture.repository.EventRepository;
import com.example.github_event_capture.service.impl.MonitorServiceImpl;

@Service
public final class KafkaDatabaseConsumer {
    private final ObjectMapper mapper = new ObjectMapper(); // map to dto object
    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaDatabaseConsumer.class);
    private MonitorServiceImpl monitorService;

    private final EventRepository eventRepository;
    public KafkaDatabaseConsumer(EventRepository eventRepository, MonitorServiceImpl monitorService) {
        this.eventRepository = eventRepository;
        this.monitorService = monitorService;
    }

    /* Fetch events from kafka and push them to the database*/
    @KafkaListener(topics = "github-event-topic", groupId = "database-consumer")
    public void PushToDatabase(ConsumerRecord<String, String> record) {
        /* log key-value pair of the event */
        LOGGER.info("Event Key: " + record.key());
        LOGGER.info("Event content: " + record.value());
        /* use key to get the type of event */
        Class <? extends Event> EventClass = EventAccess.getEventObj(record.key());
        /* map to corresponding DTO */
        try {
            /* store necessary info in dto entity */
            Event eventObj = mapper.readValue(record.value(), EventClass);
            LOGGER.info("deserialized event structure: {}", mapper.writeValueAsString(eventObj));
            //LOGGER.info("Runtime type of eventObj: {}", eventObj.getClass().getName());
            /* write to the database */
            eventRepository.save(eventObj);
        } catch (JsonProcessingException e) {
            LOGGER.error("deserialization fail: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
