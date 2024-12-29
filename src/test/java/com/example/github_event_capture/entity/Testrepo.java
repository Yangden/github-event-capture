package com.example.github_event_capture.entity;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.annotation.Id;

@Document(collection = "testdb")
public class Testrepo extends AbstractDocument{
    private String name;
    private String answer;

    public Testrepo(String name, String answer) {
        super();
        this.name = name;
        this.answer = answer;
    }

    public String getName() {
        return name;
    }

    public String getAnswer() {
        return answer;
    }
}
