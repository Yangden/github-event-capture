package com.example.github_event_capture.service.impl;

import java.util.concurrent.ConcurrentHashMap;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;


/********************************
implement MonitorService interface
*Use micrometer implementation of prometheus
******************************************/

@Service
public class MonitorServiceImpl {
    private final PrometheusMeterRegistry prometheusMeterRegistry;
    /* database read & write counters */
    private Counter mongodb_write_count;
    private Counter mongodb_read_count;
    private Counter postgres_write_count;
    private Counter postgres_read_count;
    /* measure event sending rate in sqs producer */
    private Counter event_count_afterDelete;
    /* measure event rate at the receiver side of sqs */
    private Counter event_count_sqsConsumer;
    private Counter event_count_beforeDelete;


    public MonitorServiceImpl(PrometheusMeterRegistry prometheusMeterRegistry) {
        this.prometheusMeterRegistry = prometheusMeterRegistry;
        this.mongodb_write_count = Counter.builder("database.throughput")
                .description("monitor the number of mongodb writes")
                .tags("metrics", "write-count")
                .tags("db", "mongodb")
                .register(prometheusMeterRegistry);

        this.mongodb_read_count = Counter.builder("database.throughput")
                .description("monitor the number of mongdb reads")
                .tags("metrics", "read-count")
                .tags("db", "mongodb")
                .register(prometheusMeterRegistry);

        this.postgres_write_count = Counter.builder("database.throughput")
                .tags("metrics", "write-count")
                .tags("db", "postgres")
                .register(prometheusMeterRegistry);

        this.postgres_read_count = Counter.builder("database.throughput")
                .tags("metrics", "read-count")
                .tags("db", "postgres")
                .register(prometheusMeterRegistry);

        this.event_count_sqsConsumer = Counter.builder("event.count")
                .tags("metrics", "sqs_consumer")
                .register(prometheusMeterRegistry);

        this.event_count_beforeDelete = Counter.builder("event.count")
                .tags("metrics", "sqs_receive")
                .register(prometheusMeterRegistry);

        this.event_count_afterDelete = Counter.builder("event.count")
                .tags("metrics", "sqs_delete")
                .register(prometheusMeterRegistry);
    }

    public void recordMongoDBWrite(double amount) {
        mongodb_write_count.increment(amount);
    }

    public void recordMongoDBRead(double amount) {
        mongodb_read_count.increment(amount);
    }

    public void recordPostgresWrite(double amount) {
        postgres_write_count.increment(amount);
    }

    public void recordPostgresRead(double amount) {
        postgres_read_count.increment(amount);
    }

    public void recordEventCountSqsConsumer(double amount) {
        event_count_sqsConsumer.increment(amount);
    }

    public void recordEventCountBeforDelete(double amount) {
        event_count_beforeDelete.increment(amount);
    }

    public void recordEventCountafterDelete(double amount) {
        event_count_afterDelete.increment(amount);
    }


}
