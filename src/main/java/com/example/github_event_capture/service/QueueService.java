package com.example.github_event_capture.service;

import java.util.List;

public interface QueueService {
    public void sendMessage(String message);
    public List<String> receiveMessage();
}
