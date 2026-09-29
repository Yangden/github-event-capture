package com.example.github_event_capture.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.github_event_capture.entity.Event;
import com.example.github_event_capture.entity.EventTypeMap;
import com.example.github_event_capture.entity.RepositoryMap;
import com.mongodb.MongoBulkWriteException;
import com.mongodb.ServerAddress;
import com.mongodb.bulk.BulkWriteError;
import com.mongodb.bulk.BulkWriteResult;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.bson.BsonDocument;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.BulkOperationException;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@ExtendWith(MockitoExtension.class)
public class MongoTemplateServiceTest {

    private static final int DUPLICATE_KEY = 11000;
    private static final int DOCUMENT_VALIDATION_FAILURE = 121;

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

    /* case 3 — duplicate-key race under the unique index: rebuild and retry once */
    @Test
    public void bulkWriteRetriesOnceOnDuplicateKey() {
        BulkOperations firstOps = mock(BulkOperations.class);
        BulkOperations retryOps = mock(BulkOperations.class);
        when(mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, EventTypeMap.class))
                .thenReturn(firstOps, retryOps);
        when(firstOps.execute()).thenThrow(bulkError(DUPLICATE_KEY));
        Set<String> keys = new LinkedHashSet<>(List.of("push", "issues"));

        mongoTemplateService.bulkWrite(EventTypeMap.class, keys, 3L, "eventType", "uids");

        verify(mongoTemplate, times(2)).bulkOps(BulkOperations.BulkMode.UNORDERED, EventTypeMap.class);
        verify(retryOps, times(2)).upsert(any(Query.class), any(Update.class));
        verify(retryOps).execute();
    }

    /* case 4 — non-duplicate errors propagate, no retry */
    @Test
    public void bulkWriteDoesNotRetryOnOtherErrors() {
        when(mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, EventTypeMap.class))
                .thenReturn(ops);
        when(ops.execute()).thenThrow(bulkError(DOCUMENT_VALIDATION_FAILURE));

        assertThatThrownBy(() -> mongoTemplateService.bulkWrite(
                EventTypeMap.class, Set.of("push"), 3L, "eventType", "uids"))
                .isInstanceOf(BulkOperationException.class);

        verify(mongoTemplate, times(1)).bulkOps(BulkOperations.BulkMode.UNORDERED, EventTypeMap.class);
    }

    /* case 5 — retry is bounded to one attempt */
    @Test
    public void bulkWriteRetryIsBoundedToOneAttempt() {
        BulkOperations firstOps = mock(BulkOperations.class);
        BulkOperations retryOps = mock(BulkOperations.class);
        when(mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, EventTypeMap.class))
                .thenReturn(firstOps, retryOps);
        when(firstOps.execute()).thenThrow(bulkError(DUPLICATE_KEY));
        when(retryOps.execute()).thenThrow(bulkError(DUPLICATE_KEY));

        assertThatThrownBy(() -> mongoTemplateService.bulkWrite(
                EventTypeMap.class, Set.of("push"), 3L, "eventType", "uids"))
                .isInstanceOf(BulkOperationException.class);

        verify(mongoTemplate, times(2)).bulkOps(BulkOperations.BulkMode.UNORDERED, EventTypeMap.class);
    }

    /* builds the exception DefaultBulkOperations really throws for a failed bulk write */
    private static BulkOperationException bulkError(int code) {
        MongoBulkWriteException cause = new MongoBulkWriteException(
                BulkWriteResult.unacknowledged(),
                List.of(new BulkWriteError(code, "write error", new BsonDocument(), 0)),
                null, new ServerAddress(), Set.of());
        return new BulkOperationException(cause.getMessage(), cause);
    }

    /* case 6 — saveEvent passthrough */
    @Test
    public void saveEventDelegatesToMongoTemplate() {
        Event event = new Event();

        mongoTemplateService.saveEvent(event);

        verify(mongoTemplate).save(event);
    }

}
