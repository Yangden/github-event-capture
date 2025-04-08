package com.example.github_event_capture.entity;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import java.util.ArrayList;
import java.util.List;


@Document(collection = "EventTypeSubscribers")
public class EventTypeMap {
    @Field("eventType")
    private String eventType;
    @Field("uids")
    private List<Long> uids = new ArrayList<>();

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public void addUid(long uid) {
        this.uids.add(uid);
    }

    public String getEventType() {
        return eventType;
    }

    public List<Long> getUids() {
        return uids;
    }
}
