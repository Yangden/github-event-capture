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
import com.example.github_event_capture.repository.EventTypeMapRepository;
import com.example.github_event_capture.service.impl.QueueServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.github_event_capture.entity.dto.QueueMessageDTO;
import com.example.github_event_capture.entity.EventTypeMap;
import java.util.List;
import java.util.Optional;

@Service
public class FilteredEventConsumer {
    private final FilterRepository filterRepository;
    private final UserRepository userRepository;
    private final EventTypeMapRepository eventTypeMapRepository;
    private final QueueServiceImpl queueService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final QueueServiceImpl queueServiceImpl;

    public FilteredEventConsumer(FilterRepository filterRepository, UserRepository userRepository,
                                 QueueServiceImpl queueService, QueueServiceImpl queueServiceImpl,
                                 EventTypeMapRepository eventTypeMapRepository) {
        this.filterRepository = filterRepository;
        this.userRepository = userRepository;
        this.queueService = queueService;
        this.queueServiceImpl = queueServiceImpl;
        this.eventTypeMapRepository = eventTypeMapRepository;
    }
    private static final Logger LOGGER = LoggerFactory.getLogger(FilteredEventConsumer.class);

    @KafkaListener(topics = "github-event-topic", groupId = "filter-consumer")
    public void ApplyFilters(ConsumerRecord<String, String> record) {
        LOGGER.info("The event filter consumer receives the event");

        /* get the content of the consumed event */
        String eventType = record.key();
        String eventContent = record.value();

        /* get the list of user ids */
        Optional<EventTypeMap> mapContent = eventTypeMapRepository.findByEventType(eventType);
        EventTypeMap map = mapContent.get();
        List<Long> uids = map.getUids();

        /* retrieve user emails */
        List<String> emails = userRepository.fetchEmailsbyUids(uids);

        /* send messages to amazon sqs */
        for (String email : emails) {
            /* construct the message sent to the queue*/
            QueueMessageDTO queueMessageDTO = new QueueMessageDTO();
            queueMessageDTO.setEmail(email);
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
