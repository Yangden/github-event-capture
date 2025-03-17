package com.example.github_event_capture.service;

import java.util.List;

public interface EmailSenderService {
    public void SendEmail(String sender, String receiver, String subject, String bodyHTML);
}
