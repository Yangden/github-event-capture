package com.example.github_event_capture.service;

import com.example.github_event_capture.repository.FilterRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.stereotype.Service;
import org.springframework.kafka.annotation.KafkaListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.github_event_capture.repository.UserRepository;
import com.example.github_event_capture.repository.EventTypeMapRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.github_event_capture.entity.dto.QueueMessageDTO;
import com.example.github_event_capture.entity.EventTypeMap;
import com.example.github_event_capture.service.impl.MonitorServiceImpl;
import com.example.github_event_capture.service.impl.AsyncQueueserviceImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class FilteredEventConsumer {
    private final FilterRepository filterRepository;
    private final UserRepository userRepository;
    private final EventTypeMapRepository eventTypeMapRepository;
    private final AsyncQueueserviceImpl asyncQueueService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final MonitorServiceImpl monitorService;

    public FilteredEventConsumer(FilterRepository filterRepository, UserRepository userRepository,
                                 EventTypeMapRepository eventTypeMapRepository, MonitorServiceImpl monitorService,
                                 AsyncQueueserviceImpl asyncQueueService) {
        this.filterRepository = filterRepository;
        this.userRepository = userRepository;
        this.eventTypeMapRepository = eventTypeMapRepository;
        this.monitorService = monitorService;
        this.asyncQueueService = asyncQueueService;
    }
    private static final Logger LOGGER = LoggerFactory.getLogger(FilteredEventConsumer.class);

    @KafkaListener(topics = "github-event-topic", groupId = "filter-consumer")
    public void ApplyFilters(ConsumerRecord<String, String> record) {
        // metrics, measure the consume rate
        LOGGER.info("The event filter consumer receives the event");

        /* get the content of the consumed event */
        String eventType = record.key();
        String eventContent = record.value();

        /* get the list of user ids */
        Optional<EventTypeMap> mapContent = eventTypeMapRepository.findByEventType(eventType);
        monitorService.recordMongoDBRead(1);

        /* retreive uids to query emails*/
        EventTypeMap map = mapContent.get();
        List<Long> uids = map.getUids();
        LOGGER.info(String.format("got uids %s", uids));

        /* retrieve user emails */
        List<String> emails = userRepository.findEmailsByUids(uids);
        monitorService.recordPostgresRead(1);

        /* send messages to amazon sqs */
        List<String> messages = new ArrayList<>();
       for (String email : emails) {
            //* construct the message sent to the queue*//*
            QueueMessageDTO queueMessageDTO = new QueueMessageDTO();
            queueMessageDTO.setEmail(email);
            queueMessageDTO.setEventType(eventType);
            queueMessageDTO.setEvent(eventContent);

            try {
                String message = mapper.writeValueAsString(queueMessageDTO); // transform the DTO object to string
                messages.add(message);
            } catch (JsonProcessingException e) {
                LOGGER.error(e.getMessage());
            }
       }

       asyncQueueService.sendMessage(messages);
    }



}
