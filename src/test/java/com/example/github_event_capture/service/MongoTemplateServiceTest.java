package com.example.github_event_capture.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.github_event_capture.entity.Event;
import com.example.github_event_capture.entity.EventTypeMap;
import com.example.github_event_capture.entity.RepositoryMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@ExtendWith(MockitoExtension.class)
public class MongoTemplateServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private BulkOperations ops;

    @InjectMocks
    private MongoTemplateService mongoTemplateService;

    /* case 1 — collection routing */
    @Test
    public void bulkWriteRoutesToEventTypeMapCollection() {
        when(mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, EventTypeMap.class))
                .thenReturn(ops);

        mongoTemplateService.bulkWrite(EventTypeMap.class, Set.of("push"), 3L, "eventType", "uids");

        verify(mongoTemplate).bulkOps(BulkOperations.BulkMode.UNORDERED, EventTypeMap.class);
        verify(ops).execute();
    }

    @Test
    public void bulkWriteRoutesToRepositoryMapCollection() {
        when(mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, RepositoryMap.class))
                .thenReturn(ops);

        mongoTemplateService.bulkWrite(RepositoryMap.class, Set.of("repoA"), 3L, "repository", "uids");

        verify(mongoTemplate).bulkOps(BulkOperations.BulkMode.UNORDERED, RepositoryMap.class);
        verify(ops).execute();
    }

    /* case 2 — batch content: one upsert per key, then a single execute */
    @Test
    public void bulkWriteQueuesOneUpsertPerKeyThenExecutes() {
        when(mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, EventTypeMap.class))
                .thenReturn(ops);
        Set<String> keys = new LinkedHashSet<>(List.of("push", "issues"));

        mongoTemplateService.bulkWrite(EventTypeMap.class, keys, 3L, "eventType", "uids");

        ArgumentCaptor<Query> queries = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updates = ArgumentCaptor.forClass(Update.class);
        InOrder order = inOrder(ops);
        order.verify(ops, times(2)).upsert(queries.capture(), updates.capture());
        order.verify(ops).execute();

        assertThat(queries.getAllValues())
                .extracting(query -> query.getQueryObject().getString("eventType"))
                .containsExactlyInAnyOrder("push", "issues");
        for (Update update : updates.getAllValues()) {
            Document addToSet = update.getUpdateObject().get("$addToSet", Document.class);
            assertThat(addToSet.getLong("uids")).isEqualTo(3L);
        }
    }

    /* case 6 — saveEvent passthrough */
    @Test
    public void saveEventDelegatesToMongoTemplate() {
        Event event = new Event();

        mongoTemplateService.saveEvent(event);

        verify(mongoTemplate).save(event);
    }

}
