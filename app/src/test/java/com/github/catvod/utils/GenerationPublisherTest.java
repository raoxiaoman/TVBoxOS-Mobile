package com.github.catvod.utils;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GenerationPublisherTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void jarLoaderGenerationRejectsInFlightOldPublishAndKeepsNewCache() throws Exception {
        GenerationPublisher publisher = new GenerationPublisher();
        File target = new File(temporaryFolder.getRoot(), "dynamic.jar");
        long oldGeneration = publisher.current();
        CountDownLatch oldReady = new CountDownLatch(1);
        CountDownLatch allowOldPublish = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<Boolean> oldPublish = executor.submit(() -> {
                oldReady.countDown();
                allowOldPublish.await();
                return publisher.publishIfCurrent(oldGeneration, () -> publish(target, "old"));
            });
            assertTrue(oldReady.await(5, TimeUnit.SECONDS));

            publisher.advance();
            assertTrue(publisher.publishIfCurrent(publisher.current(), () -> publish(target, "new")));
            allowOldPublish.countDown();

            assertFalse(oldPublish.get(5, TimeUnit.SECONDS));
            assertArrayEquals("new".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(target.toPath()));
        } finally {
            allowOldPublish.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static void publish(File target, String value) throws Exception {
        File temp = DynamicCode.createTempFile(target);
        try (FileOutputStream output = new FileOutputStream(temp)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        }
        DynamicCode.publishReadOnly(temp, target);
    }
}
