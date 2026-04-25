package com.example.github_event_capture.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "ttlConfig")
public class ttlConfig {
    @Id
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

    public void setUid(long uid) {
        this.uid = uid;
    }

    public void setDay(int day) {
        this.day = day;
    }

    public void setHour(int hour) {
        this.hour = hour;
    }
}
