package com.example.github_event_capture.service;

import com.example.github_event_capture.repository.FilterRepository;
import org.springframework.stereotype.Service;
import org.springframework.kafka.annotation.KafkaListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import com.example.github_event_capture.entity.Filters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

@Service
public class EventFilterConsumer {
    private final FilterRepository filterRepository;
    public EventFilterConsumer(FilterRepository filterRepository) {
        this.filterRepository = filterRepository;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(EventFilterConsumer.class);
    @KafkaListener(topics = "github-event-topic", groupId = "filter-consumer")
    public void ApplyFilters(ConsumerRecord<String, String> record) {
        /* From user session get user ID */

        /* get the filter record */
        Optional<Filters> FilterVal = filterRepository.findByUserId(1);

        if (!FilterVal.isPresent()) {
            LOGGER.info("not present values");
            return;
        }

        /* filter the consumed events, send out the notification if needed */
        String eventType = record.key();
        Filters filter = FilterVal.get();
        if (filter.getEventTypes().contains(eventType)) { // send notification
            // notification service : to be completed
            LOGGER.info("filtered event types" + filter.getEventTypes());
            LOGGER.info("successful handle");
        }
    }



}
