package com.example.github_event_capture.controller;

import com.example.github_event_capture.service.impl.EventProducerImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.github_event_capture.service.EventProducer;


@RestController
public class WebHookController {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebHookController.class);

    private final EventProducer<String, String> eventProducer;

    public WebHookController(EventProducerImpl<String, String> eventProducerImpl) {
        this.eventProducer = eventProducerImpl;
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> eventDeliveryHandler(
            @RequestHeader("x-github-event") String eventType,
            @RequestBody String payload) {
        LOGGER.info("Received payload: " + payload);
        LOGGER.info("Event Type: " + eventType);

        /* push raw payload of the request into kafka */
        if (!eventType.equals("ping")) {
            eventProducer.sendEvent(eventType, payload);
        }

        /* return success code */
        return new ResponseEntity<>("Accepted", HttpStatus.ACCEPTED);
    }
}

