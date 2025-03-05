package com.example.github_event_capture.entity.dto;

import java.util.HashSet;

public class FiltersDTO {
    private HashSet<String> eventTypes;

    public HashSet<String> getEventTypes() {
        return eventTypes;
    }

    public void setEventTypes(HashSet<String> eventTypes) {
        this.eventTypes = eventTypes;
    }
}
