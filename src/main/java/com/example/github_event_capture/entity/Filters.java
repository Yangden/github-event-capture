package com.example.github_event_capture.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.HashSet;

@Document(collection = "Filters")
public class Filters {
    @Field("uid")
    private int uid;

    /* filters */
    @Field("EventTypes")
    private HashSet<String> EventTypes;
    // other filters : may include 'labels/tags'

    public HashSet<String> getEventTypes() {
        return EventTypes;
    }
    public void setEventTypes(HashSet<String> eventTypes) {
        this.EventTypes = eventTypes;
    }

}
