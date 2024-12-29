package com.example.github_event_capture.service;

public interface EventProducer<K, V> {
    public void sendEvent(K key, V value);
}
