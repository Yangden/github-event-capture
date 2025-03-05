package com.example.github_event_capture.service;

import com.example.github_event_capture.utils.Result;
import com.example.github_event_capture.entity.dto.FiltersDTO;

public interface EventFiltersService {
    public Result createFilters(FiltersDTO filters);
    public Result clearAllFilters();
}
