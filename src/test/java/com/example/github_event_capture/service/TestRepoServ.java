package com.example.github_event_capture.service;


import com.example.github_event_capture.entity.Testrepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.example.github_event_capture.repository.TestRepository;

@Service
public class TestRepoServ {
    @Autowired
    private TestRepository testRepo;

    public void WriteTest(Testrepo t) {
        testRepo.save(t);
    }

}
