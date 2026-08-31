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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FileLoadCoordinatorTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void separateCoordinatorsLoadAndPublishSameAbsolutePathOnlyOnce() throws Exception {
        File target = new File(temporaryFolder.getRoot(), "shared.jar");
        FileLoadCoordinator firstCaller = new FileLoadCoordinator();
        FileLoadCoordinator secondCaller = new FileLoadCoordinator();
        AtomicInteger publishCount = new AtomicInteger();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch allowFirstPublish = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<FileLoadCoordinator.Result> first = executor.submit(() -> firstCaller.getOrLoad(target, () -> {
                firstStarted.countDown();
                allowFirstPublish.await();
                publish(target, "first");
                publishCount.incrementAndGet();
                return FileLoadCoordinator.Result.success();
            }));
            assertTrue(firstStarted.await(5, TimeUnit.SECONDS));

            Future<FileLoadCoordinator.Result> second = executor.submit(() -> secondCaller.getOrLoad(target, () -> {
                publish(target, "second");
                publishCount.incrementAndGet();
                return FileLoadCoordinator.Result.success();
            }));
            allowFirstPublish.countDown();

            assertTrue(first.get(5, TimeUnit.SECONDS).isSuccess());
            assertTrue(second.get(5, TimeUnit.SECONDS).isSuccess());
            assertEquals(1, publishCount.get());
            assertArrayEquals("first".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(target.toPath()));
        } finally {
            allowFirstPublish.countDown();
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
