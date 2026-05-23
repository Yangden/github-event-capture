package com.example.github_event_capture.controller;

import com.example.github_event_capture.entity.dto.TtlConfigDTO;
import com.example.github_event_capture.service.impl.TtlConfigServiceImpl;
import com.example.github_event_capture.utils.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ttl")
public class TtlConfigController {
    private final TtlConfigServiceImpl ttlConfigService;

    public TtlConfigController(TtlConfigServiceImpl ttlConfigService) {
        this.ttlConfigService = ttlConfigService;
    }

    @PostMapping
    public ResponseEntity<String> setTtl(@RequestBody TtlConfigDTO ttlConfigDTO) {
        Result<String> result = ttlConfigService.setTtl(ttlConfigDTO);
        if (!result.isSuccess()) {
            return new ResponseEntity<>(result.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return ResponseEntity.ok(result.getMessage());
    }
}
