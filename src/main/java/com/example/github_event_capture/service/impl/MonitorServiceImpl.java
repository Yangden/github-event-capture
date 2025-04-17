package com.example.github_event_capture.service.impl;

import io.micrometer.core.instrument.Counter;
import org.springframework.stereotype.Service;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;


/********************************
implement MonitorService interface
*Use micrometer implementation of prometheus
******************************************/

@Service
public class MonitorServiceImpl {
    private final PrometheusMeterRegistry prometheusMeterRegistry;
    private Counter mongodb_write_count;
    private Counter mongodb_read_count;
    private Counter postgres_write_count;
    private Counter postgres_read_count;

    public MonitorServiceImpl(PrometheusMeterRegistry prometheusMeterRegistry) {
        this.prometheusMeterRegistry = prometheusMeterRegistry;
        this.mongodb_write_count = Counter.builder("database.throughput")
                .description("monitor the number of mongodb writes")
                .tags("metrics", "write-count")
                .tags("db", "mongodb")
                .register(prometheusMeterRegistry);
        mongodb_write_count.increment();

        this.mongodb_read_count = Counter.builder("database.throughput")
                .description("monitor the number of mongdb reads")
                .tags("metrics", "read-count")
                .tags("db", "mongodb")
                .register(prometheusMeterRegistry);
        mongodb_read_count.increment();

        this.postgres_write_count = Counter.builder("database.throughput")
                .tags("metrics", "write-count")
                .tags("db", "postgres")
                .register(prometheusMeterRegistry);
        postgres_write_count.increment();

        this.postgres_read_count = Counter.builder("database.throughput")
                .tags("metrics", "read-count")
                .tags("db", "postgres")
                .register(prometheusMeterRegistry);
        postgres_read_count.increment();
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

}
