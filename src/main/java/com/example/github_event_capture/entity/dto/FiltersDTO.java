package com.example.github_event_capture.entity.dto;

import java.util.HashSet;

public class FiltersDTO {
    private HashSet<String> eventTypes;
    private HashSet<String> repositories;

    public HashSet<String> getEventTypes() {
        return eventTypes;
    }

    public HashSet<String> getRepositories() {
        return repositories;
    }

    public void setEventTypes(HashSet<String> eventTypes) {
        this.eventTypes = eventTypes;
    }
    public void setRepositories(HashSet<String> repositories) {
        this.repositories = repositories;
    }
}
