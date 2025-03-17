package com.example.github_event_capture.service.impl;

import com.example.github_event_capture.service.EmailSenderService;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;
import org.springframework.stereotype.Service;

@Service
public class EmailSenderServiceImpl implements EmailSenderService {
    private final ProfileCredentialsProvider profileCredentialsProvider = ProfileCredentialsProvider.create("my-dev-profile");
    private final SesClient sesClient = SesClient.builder()
            .region(Region.US_EAST_1)
            .credentialsProvider(profileCredentialsProvider).build();


    public void SendEmail(String sender, String receiver, String subject, String bodyHTML) {
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
            System.out.println("Sending email to " + receiver + "\n");
            sesClient.sendEmail(req);
        } catch (SesException e) {
            System.err.println(e.awsErrorDetails().errorMessage());
        }
    }
}
