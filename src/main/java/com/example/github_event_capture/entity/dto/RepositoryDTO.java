package com.example.github_event_capture.entity.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RepositoryDTO {
    @JsonProperty("full_name")
    private String repoName;

    public String getRepoName() {
        return repoName;
    }
}
