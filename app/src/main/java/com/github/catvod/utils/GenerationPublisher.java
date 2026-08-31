package com.github.catvod.utils;

public final class GenerationPublisher {
    private long generation;

    public synchronized long current() {
        return generation;
    }

    public synchronized void advance() {
        generation++;
    }

    public synchronized boolean isCurrent(long expectedGeneration) {
        return generation == expectedGeneration;
    }

    public synchronized boolean publishIfCurrent(long expectedGeneration, Publisher publisher) throws Exception {
        if (generation != expectedGeneration) {
            return false;
        }
        publisher.publish();
        return true;
    }

    public interface Publisher {
        void publish() throws Exception;
    }
}
