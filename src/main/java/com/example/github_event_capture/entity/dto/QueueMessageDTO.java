package com.example.github_event_capture.entity.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class QueueMessageDTO {
    @JsonProperty("event")
    private String event;
    @JsonProperty("email")
    private String email;
    @JsonProperty("eventType")
    private String eventType;

    public String getEvent() {
        return event;
    }

    public String getEmail() {
        return email;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }
}
