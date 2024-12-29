package com.example.github_event_capture;

import com.example.github_event_capture.service.TestRepoServ;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.example.github_event_capture.entity.Testrepo;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootTest
@EnableMongoRepositories(basePackages = "com.example.github_event_capture.repository")
public class MongoTest {
    @Autowired
    private TestRepoServ testRepoServ;

    @Test
    public void TestWrite() {
        Testrepo testobj = new Testrepo("Joker", "deng");
        testRepoServ.WriteTest(testobj); //
    }
}
