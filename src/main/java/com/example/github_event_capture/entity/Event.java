package com.example.github_event_capture.entity;

import org.springframework.data.annotation.Id;

public abstract class Event {
    @Id
    private String id;


    public String getId() {
        return id;
    }


}