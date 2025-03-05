package com.example.github_event_capture.controller;

import com.example.github_event_capture.entity.Event;
import com.example.github_event_capture.security.CustomUserDetail;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.example.github_event_capture.service.impl.EventFiltersServiceImpl;
import com.example.github_event_capture.entity.dto.FiltersDTO;
import com.example.github_event_capture.utils.Result;

@RestController(value = "/api/filters")
public class EventFilterController {
    private EventFiltersServiceImpl filterServices;
    public EventFilterController(EventFiltersServiceImpl filterServices) {
        this.filterServices = filterServices;
    }
    /* set up filters */
    @PostMapping(value = "create")
    public ResponseEntity<String> createEventFilters(@RequestBody FiltersDTO filtersDTO) {
        Result<String> result = filterServices.createFilters(filtersDTO);
        if (!result.isSuccess()) {
            return new ResponseEntity<String>(result.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return ResponseEntity.ok(result.getMessage());
    }

    /* edit filters */

    /* delete filters */
    @PostMapping(value = "deleteAll")
    public ResponseEntity<String> deleteAllEventFilters() {
        Result result = filterServices.clearAllFilters();
        if (!result.isSuccess()) {
            return new ResponseEntity<String>(result.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return ResponseEntity.ok(result.getMessage());
    }
}
