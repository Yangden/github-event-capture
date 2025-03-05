package com.example.github_event_capture.service;

import com.example.github_event_capture.repository.FilterRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.kafka.annotation.KafkaListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import com.example.github_event_capture.entity.Filters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Optional;
import org.springframework.security.core.context.SecurityContextHolder;
import com.example.github_event_capture.security.CustomUserDetail;

@Service
public class FilteredEventConsumer {
    private final FilterRepository filterRepository;

    public FilteredEventConsumer(FilterRepository filterRepository) {
        this.filterRepository = filterRepository;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(FilteredEventConsumer.class);
    @KafkaListener(topics = "github-event-topic", groupId = "filter-consumer")
    public void ApplyFilters(ConsumerRecord<String, String> record) {
        Iterable<Filters> filtersRecords = filterRepository.findAll();
        /* from security context get uid */
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetail customUserDetail = (CustomUserDetail) authentication.getPrincipal();

        /* get the filter record */
        Optional<Filters> FilterVal = filterRepository.findByUserId(customUserDetail.getUid());

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
