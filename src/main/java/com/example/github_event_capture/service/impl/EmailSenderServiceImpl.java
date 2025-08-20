package com.example.github_event_capture.service.impl;

import com.example.github_event_capture.entity.dto.QueueMessageDTO;
import com.example.github_event_capture.service.EmailSenderService;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.SesAsyncClient;
import software.amazon.awssdk.services.ses.model.*;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

@Service
public class EmailSenderServiceImpl implements EmailSenderService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmailSenderServiceImpl.class);
    private final ProfileCredentialsProvider profileCredentialsProvider = ProfileCredentialsProvider.create("my-dev-profile");
    private final SesClient sesClient = SesClient.builder()
            .region(Region.US_EAST_1)
            .credentialsProvider(profileCredentialsProvider).build();
    private final SesAsyncClient sesAsyncClient = SesAsyncClient.builder()
            .region(Region.US_EAST_1)
            .credentialsProvider(profileCredentialsProvider).build();

   public SendEmailRequest buildRequest(String sender, String receiver, String subject, String bodyHTML) {
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

       return SendEmailRequest.builder()
               .destination(destination)
               .message(msg)
               .source(sender)
               .build();
   }

   public void SendEmail(String sender, String receiver, String subject, String bodyHTML) {
        SendEmailRequest req = buildRequest(sender, receiver, subject, bodyHTML);
        try {
            System.out.println("Sending email to " + receiver + "\n");
            sesClient.sendEmail(req);
        } catch (SesException e) {
            System.err.println(e.awsErrorDetails().errorMessage());
        }
   }

   public CompletableFuture<SendEmailResponse> SendEmailAsync(String sender, String receiver, String subject, String bodyHTML) {
       SendEmailRequest req = buildRequest(sender, receiver, subject, bodyHTML);
       return sesAsyncClient.sendEmail(req)
               .whenComplete((response, throwable) -> {
                   if (throwable != null) {
                       LOGGER.error(throwable.getMessage());
                   } else {
                       LOGGER.info("email sent to " + receiver + "\n");
                   }
               });
   }
}
