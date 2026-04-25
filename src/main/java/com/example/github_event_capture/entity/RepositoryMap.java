package com.example.github_event_capture.entity;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "RepositorySubscribers")
public class RepositoryMap {
    @Field("repository")
    private String repository;

    @Field("uids")
    private List<Long> uids;

    public void setRepository(String repository) {
        this.repository = repository;
    }

    public void addUid(long uid) {
        if (uids == null) {
            uids = new ArrayList<>();
        }
        uids.add(uid);
    }

    public String getRepository() {
        return repository;
    }

    public List<Long> getUids() {
        return uids;
    }
}
