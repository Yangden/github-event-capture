package com.example.github_event_capture.entity;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "AlertHistory")
public class AlertRecord {
    @Id
    private String id;

    @Field("issueId")
    private long issueId;

    @Field("uid")
    private long uid;

    @Field("alertedAt")
    private Instant alertedAt;

    public String getId() {
        return id;
    }

    public long getIssueId() {
        return issueId;
    }

    public long getUid() {
        return uid;
    }

    public Instant getAlertedAt() {
        return alertedAt;
    }

    public void setIssueId(long issueId) {
        this.issueId = issueId;
    }

    public void setUid(long uid) {
        this.uid = uid;
    }

    public void setAlertedAt(Instant alertedAt) {
        this.alertedAt = alertedAt;
    }
}
