package com.example.github_event_capture.entity.dto;

import com.example.github_event_capture.entity.Event;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Document(collection = "PushEvents")
public class PushEventDTO extends Event {
    /* pusher information */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Pusher {
        @JsonProperty("name")
        private String name;
        @JsonProperty("email")
        private String email;

        public String getName() {
            return name;
        }

        public String getEmail() {
            return email;
        }
    }

    /* commits information related to the push */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Commit {
        @JsonProperty("message")
        String commitMessage;

        public String getCommitMessage() {
            return commitMessage;
        }
    }

    @JsonProperty("repository")
    private RepositoryDTO repository;

    @JsonProperty("pusher")
    private Pusher pusher;

    @JsonProperty("commits")
    private List<Commit> commits;


}
