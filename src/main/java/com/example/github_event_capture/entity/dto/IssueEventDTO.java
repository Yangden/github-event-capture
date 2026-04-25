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
        @JsonProperty("id")
        private long id;

        @JsonProperty("state")
        private String state;

        @JsonProperty("body")
        private String body;

        @JsonProperty("created_at")
        private Instant createdAt;

        public long getId() {
            return id;
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
        return issueInfo != null ? issueInfo.getId() : 0;
    }

    public String getRepositoryName() {
        return repository != null ? repository.getName() : null;
    }

    public Instant getCreatedAt() {
        return issueInfo != null ? issueInfo.getCreatedTime() : null;
    }
}
