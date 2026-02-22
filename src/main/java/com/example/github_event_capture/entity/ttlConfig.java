package com.example.github_event_capture.entity;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "ttlConfig")
public class ttlConfig {
    @Field("uid")
    private long uid;

    @Field("day")
    private int day;

    @Field("hour")
    private int hour;

    public long getUid() {
        return uid;
    }

    public int getDay() {
        return day;
    }

    public int getHour() {
        return hour;
    }

    public void setDay(int day) {
        this.day = day;
    }

    public void setHour(int hour) {
        this.hour = hour;
    }

    
}
