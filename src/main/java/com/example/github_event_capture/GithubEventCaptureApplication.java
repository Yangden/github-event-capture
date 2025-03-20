package com.example.github_event_capture;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication
@EnableMongoRepositories(basePackages = "com.example.github_event_capture.repository")
@EnableAsync
@EnableScheduling
public class GithubEventCaptureApplication {
	public static void main(String[] args) {
		SpringApplication.run(GithubEventCaptureApplication.class, args);
	}

}
