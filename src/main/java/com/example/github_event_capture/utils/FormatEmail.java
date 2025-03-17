package com.example.github_event_capture.utils;

public class FormatEmail {
    public static String getFormatEmail(String event) {
        String emailBody = "<html>" + "<body>" + "<p>" + event + "</p>" + "</body>" + "</html>";
        return emailBody;
    }
}
