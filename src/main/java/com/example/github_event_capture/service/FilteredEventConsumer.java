package com.example.github_event_capture.service;

import com.example.github_event_capture.entity.User;
import com.example.github_event_capture.repository.FilterRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.stereotype.Service;
import org.springframework.kafka.annotation.KafkaListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import com.example.github_event_capture.entity.Filters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.github_event_capture.repository.UserRepository;
import com.example.github_event_capture.service.impl.QueueServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.github_event_capture.entity.dto.QueueMessageDTO;

@Service
public class FilteredEventConsumer {
    private final FilterRepository filterRepository;
    private final UserRepository userRepository;
    private final QueueServiceImpl queueService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final QueueServiceImpl queueServiceImpl;

    public FilteredEventConsumer(FilterRepository filterRepository, UserRepository userRepository,
                                 QueueServiceImpl queueService, QueueServiceImpl queueServiceImpl) {
        this.filterRepository = filterRepository;
        this.userRepository = userRepository;
        this.queueService = queueService;
        this.queueServiceImpl = queueServiceImpl;
    }
    private static final Logger LOGGER = LoggerFactory.getLogger(FilteredEventConsumer.class);

    @KafkaListener(topics = "github-event-topic", groupId = "filter-consumer")
    public void ApplyFilters(ConsumerRecord<String, String> record) {
        LOGGER.info("The event filter consumer receives the event");

        /* get the content of the consumed event */
        String eventType = record.key();
        String eventContent = record.value();

        /* Fetch all records of filters */
        Iterable<Filters> filtersRecords = filterRepository.findAll();
        /* apply filters of each record */
        for (Filters filters : filtersRecords) {
            if (!filters.getEventTypes().contains(eventType)) { // consumed event does not pass the filter
                continue;
            }
            /* fetch user information to get the email */
            User user = userRepository.findById(filters.getUid());
            /* construct the message sent to the queue*/
            QueueMessageDTO queueMessageDTO = new QueueMessageDTO();
            queueMessageDTO.setEmail(user.getEmail());
            queueMessageDTO.setEventType(eventType);
            queueMessageDTO.setEvent(eventContent);

            try {
                String message = mapper.writeValueAsString(queueMessageDTO); // transform the DTO object to string
                queueServiceImpl.sendMessage(message); // send the message to the queue
                LOGGER.info("send message to queue");
            } catch (JsonProcessingException e) {
                LOGGER.error(e.getMessage());
            }

        }

    }



}
