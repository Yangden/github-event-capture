package com.example.github_event_capture.entity.dto;

import com.example.github_event_capture.entity.Event;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.mongodb.core.mapping.Document;

@JsonIgnoreProperties(ignoreUnknown = true)
@Document(collection = "IssueEvents")
public class IssueEventDTO extends Event {
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class IssueInfo {
        @JsonProperty("state")
        private String state;

        @JsonProperty("body")
        private String body;


        private String getState() {
            return state;
        }

        private String getBody() {
            return body;
        }
    }

    private String action;

    @JsonProperty("issue")
    private IssueInfo issueInfo;


    public String getAction() {
        return action;
    }



}
