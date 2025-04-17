package com.example.github_event_capture.service.impl;

import com.example.github_event_capture.entity.dto.FiltersDTO;
import com.example.github_event_capture.utils.Result;
import com.example.github_event_capture.entity.Filters;
import com.example.github_event_capture.entity.EventTypeMap;
import com.example.github_event_capture.repository.FilterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.github_event_capture.utils.HttpResponseMsg;
import org.springframework.stereotype.Service;
import com.example.github_event_capture.security.SecurityContextService;
import com.example.github_event_capture.service.MongoTemplateService;
import com.example.github_event_capture.service.impl.MonitorServiceImpl;



@Service
public class EventFiltersServiceImpl {
    private final FilterRepository filterRepository;
    private final MongoTemplateService mongoTemplateService;
    private final MonitorServiceImpl monitorService;
    private final Logger LOGGER = LoggerFactory.getLogger(EventFiltersServiceImpl.class);

    public EventFiltersServiceImpl(FilterRepository filterRepository,
                                   MongoTemplateService mongoTemplateService, MonitorServiceImpl monitorService) {
        this.filterRepository = filterRepository;
        this.mongoTemplateService = mongoTemplateService;
        this.monitorService = monitorService;
    }

    public Result createFilters(FiltersDTO filtersDTO) {
        long uid = SecurityContextService.getUidFromSeucrityContext();

        try {
            /* transform the dto object to the database entity object */
            Filters filters = new Filters();
            filters.setUid(uid);
            LOGGER.info("event type: {} ", filtersDTO.getEventTypes());
            filters.setEventTypes(filtersDTO.getEventTypes());
            /* write to the filter collection */
            filterRepository.save(filters);
            /* write to the eventTypeSubscribers */
            LOGGER.info("start bulk writes to the inverted index");
            String keyField = "eventType";
            String valField = "uids";
            mongoTemplateService.setDomainClass(EventTypeMap.class);
            mongoTemplateService.setBulkOps();
            mongoTemplateService.bulkWrite(filtersDTO.getEventTypes(), uid, keyField, valField);
            monitorService.recordMongoDBWrite((double) filtersDTO.getEventTypes().size());

        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            return Result.fail("filters creation failed");
        }

        return Result.success(HttpResponseMsg.OK);

    }

    public Result clearAllFilters() {
        long uid = SecurityContextService.getUidFromSeucrityContext();
        try {
            filterRepository.deleteByUid(uid);
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            return Result.fail("clearing all filters failed");
        }
        return Result.success(HttpResponseMsg.OK);
    }

}
