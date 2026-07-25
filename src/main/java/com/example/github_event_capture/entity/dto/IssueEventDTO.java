package com.example.github_event_capture.entity.dto;

import java.time.Instant;
import com.example.github_event_capture.entity.Event;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.mongodb.core.mapping.Document;

@JsonIgnoreProperties(ignoreUnknown = true)
@Document(collection = "IssueEvents")
public class IssueEventDTO extends Event {
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class IssueInfo {
        // Persisted as "eventId" (not "id") on purpose: Spring Data Mongo remaps any field
        // literally named "id" — even nested — to the document key "_id". This field is the
        // GitHub-global issue id used to group events per issue, not a document identity, so
        // it keeps its own name. @JsonProperty("id") still binds the webhook's issue.id.
        @JsonProperty("id")
        private long eventId;

        @JsonProperty("state")
        private String state;

        @JsonProperty("body")
        private String body;

        @JsonProperty("created_at")
        private Instant createdAt;

        public long getEventId() {
            return eventId;
        }

        public String getState() {
            return state;
        }

        public String getBody() {
            return body;
        }

        public Instant getCreatedTime() {
            return createdAt;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Repository {
        @JsonProperty("name")
        private String name;

        public String getName() {
            return name;
        }
    }

    private String action;

    @JsonProperty("issue")
    private IssueInfo issueInfo;

    @JsonProperty("repository")
    private Repository repository;

    public String getAction() {
        return action;
    }

    public long getIssueId() {
        return issueInfo != null ? issueInfo.getEventId() : 0;
    }

    public String getRepositoryName() {
        return repository != null ? repository.getName() : null;
    }

    public Instant getCreatedAt() {
        return issueInfo != null ? issueInfo.getCreatedTime() : null;
    }
}
