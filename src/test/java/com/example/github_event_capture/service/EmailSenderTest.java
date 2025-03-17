package com.example.github_event_capture.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.example.github_event_capture.service.EmailSenderService;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

public class EmailSenderTest {
    private final ProfileCredentialsProvider profileCredentialsProvider = ProfileCredentialsProvider.create("my-dev-profile");
    private final SesClient sesClient = SesClient.builder()
            .region(Region.US_EAST_1)
            .credentialsProvider(profileCredentialsProvider).build();

    private void SendEmail(String sender, String receiver, String subject, String bodyHTML) {
        Destination destination = Destination.builder()
                .toAddresses(receiver)
                .build();
        Content content = Content.builder()
                .data(bodyHTML)
                .build();
        Content sub = Content.builder()
                .data(subject)
                .build();
        Body body = Body.builder()
                .html(content)
                .build();
        Message msg = Message.builder()
                .subject(sub)
                .body(body)
                .build();
        SendEmailRequest req = SendEmailRequest.builder()
                .destination(destination)
                .message(msg)
                .source(sender)
                .build();
        try {
            System.out.println("Sending email to " + sender + "\n");
            sesClient.sendEmail(req);
        } catch (SesException e) {
            System.err.println(e.awsErrorDetails().errorMessage());
        }
    }
    @DisplayName("send email")
    @Test
    public void sendEmailTest() {
        String sender = "yangdeng2001@gmail.com";
        String recipient = "dengyang2001@outlook.com";
        String subject = "Test";
        String bodyHTML = "<html>" + "<head></head>" + "<body>" + "<h1>Hello!</h1>"
                + "<p> See the list of customers.</p>" + "</body>" + "</html>";
        SendEmail(sender, recipient, subject, bodyHTML);
    }
}
