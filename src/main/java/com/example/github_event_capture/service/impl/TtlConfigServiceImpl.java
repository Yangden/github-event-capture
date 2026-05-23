package com.example.github_event_capture.service.impl;

import com.example.github_event_capture.entity.dto.TtlConfigDTO;
import com.example.github_event_capture.entity.ttlConfig;
import com.example.github_event_capture.repository.TtlConfigRepository;
import com.example.github_event_capture.security.SecurityContextService;
import com.example.github_event_capture.utils.HttpResponseMsg;
import com.example.github_event_capture.utils.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TtlConfigServiceImpl {
    private final TtlConfigRepository ttlConfigRepository;
    private final Logger LOGGER = LoggerFactory.getLogger(TtlConfigServiceImpl.class);

    public TtlConfigServiceImpl(TtlConfigRepository ttlConfigRepository) {
        this.ttlConfigRepository = ttlConfigRepository;
    }

    public Result setTtl(TtlConfigDTO dto) {
        long uid = SecurityContextService.getUidFromSeucrityContext();
        try {
            ttlConfig config = new ttlConfig();
            config.setUid(uid);
            config.setDay(dto.getDay());
            config.setHour(dto.getHour());
            ttlConfigRepository.save(config);
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            return Result.fail("TTL configuration failed");
        }
        return Result.success(HttpResponseMsg.OK);
    }
}
