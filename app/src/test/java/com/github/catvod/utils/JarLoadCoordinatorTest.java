package com.github.catvod.utils;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class JarLoadCoordinatorTest {

    @Test
    public void sameRuntimeKeyInitializesOnceAndSharesTheLoadedInstance() throws Exception {
        JarLoadCoordinator<Object> coordinator = new JarLoadCoordinator<>();
        Object runtime = new Object();
        AtomicInteger initializationCount = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch allowInitialization = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<JarLoadCoordinator.Result<Object>> first = executor.submit(() -> coordinator.getOrLoad(
                    "same-jar", () -> {
                        initializationCount.incrementAndGet();
                        started.countDown();
                        allowInitialization.await();
                        return JarLoadCoordinator.Result.success(runtime);
                    }));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            Future<JarLoadCoordinator.Result<Object>> second = executor.submit(() -> coordinator.getOrLoad(
                    "same-jar", () -> JarLoadCoordinator.Result.success(new Object())));
            allowInitialization.countDown();

            assertSame(runtime, first.get(5, TimeUnit.SECONDS).getValue());
            assertSame(runtime, second.get(5, TimeUnit.SECONDS).getValue());
            assertEquals(1, initializationCount.get());
        } finally {
            allowInitialization.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void sameKeyLoadsOnlyOnce() throws Exception {
        JarLoadCoordinator<String> coordinator = new JarLoadCoordinator<>();
        AtomicInteger loadCount = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(10);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<Future<JarLoadCoordinator.Result<String>>> results = new ArrayList<>();

        try {
            for (int i = 0; i < 10; i++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return coordinator.getOrLoad("subscription", () -> {
                        loadCount.incrementAndGet();
                        Thread.sleep(100);
                        return JarLoadCoordinator.Result.success("loader");
                    });
                }));
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            for (Future<JarLoadCoordinator.Result<String>> future : results) {
                JarLoadCoordinator.Result<String> result = future.get(5, TimeUnit.SECONDS);
                assertTrue(result.isSuccess());
                assertEquals("loader", result.getValue());
            }
            assertEquals(1, loadCount.get());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void retryableFailureDoesNotStayCached() throws Exception {
        JarLoadCoordinator<String> coordinator = new JarLoadCoordinator<>();
        AtomicInteger loadCount = new AtomicInteger();

        JarLoadCoordinator.Result<String> retryable = coordinator.getOrLoad("subscription", () -> {
            loadCount.incrementAndGet();
            return JarLoadCoordinator.Result.retryableFailure();
        });
        JarLoadCoordinator.Result<String> success = coordinator.getOrLoad("subscription", () -> {
            loadCount.incrementAndGet();
            return JarLoadCoordinator.Result.success("loader");
        });

        assertTrue(retryable.isRetryableFailure());
        assertTrue(success.isSuccess());
        assertEquals("loader", success.getValue());
        assertEquals(2, loadCount.get());
    }

    @Test
    public void incompatibleResultStaysCached() throws Exception {
        JarLoadCoordinator<String> coordinator = new JarLoadCoordinator<>();
        AtomicInteger loadCount = new AtomicInteger();

        JarLoadCoordinator.Result<String> first = coordinator.getOrLoad("subscription", () -> {
            loadCount.incrementAndGet();
            return JarLoadCoordinator.Result.incompatible();
        });
        JarLoadCoordinator.Result<String> second = coordinator.getOrLoad("subscription", () -> {
            loadCount.incrementAndGet();
            return JarLoadCoordinator.Result.success("loader");
        });

        assertTrue(first.isIncompatible());
        assertTrue(second.isIncompatible());
        assertEquals(1, loadCount.get());
    }

    @Test
    public void clearDropsSubscriptionResults() throws Exception {
        JarLoadCoordinator<String> coordinator = new JarLoadCoordinator<>();
        AtomicInteger loadCount = new AtomicInteger();

        JarLoadCoordinator.Result<String> first = coordinator.getOrLoad("subscription", () -> {
            loadCount.incrementAndGet();
            return JarLoadCoordinator.Result.success("first");
        });
        coordinator.clear();
        JarLoadCoordinator.Result<String> second = coordinator.getOrLoad("subscription", () -> {
            loadCount.incrementAndGet();
            return JarLoadCoordinator.Result.success("second");
        });

        assertEquals("first", first.getValue());
        assertEquals("second", second.getValue());
        assertEquals(2, loadCount.get());
    }

    @Test
    public void loaderExceptionCanBeRetriedByLaterCaller() throws Exception {
        JarLoadCoordinator<String> coordinator = new JarLoadCoordinator<>();

        try {
            coordinator.getOrLoad("subscription", () -> {
                throw new IllegalStateException("failed");
            });
            fail("Expected loader exception");
        } catch (ExecutionException expected) {
            assertTrue(expected.getCause() instanceof IllegalStateException);
        }

        JarLoadCoordinator.Result<String> retry = coordinator.getOrLoad("subscription",
                () -> JarLoadCoordinator.Result.success("loader"));
        assertTrue(retry.isSuccess());
        assertEquals("loader", retry.getValue());
    }

    @Test
    public void clearAllowsReplacementWhileEarlierLoadIsInFlight() throws Exception {
        JarLoadCoordinator<String> coordinator = new JarLoadCoordinator<>();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch complete = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<JarLoadCoordinator.Result<String>> first = executor.submit(() -> coordinator.getOrLoad(
                    "subscription", () -> {
                        started.countDown();
                        complete.await();
                        return JarLoadCoordinator.Result.success("old");
                    }));
            assertTrue(started.await(5, TimeUnit.SECONDS));

            coordinator.clear();
            JarLoadCoordinator.Result<String> replacement = coordinator.getOrLoad("subscription",
                    () -> JarLoadCoordinator.Result.success("new"));
            complete.countDown();

            assertTrue(replacement.isSuccess());
            assertEquals("new", replacement.getValue());
            assertEquals("old", first.get(5, TimeUnit.SECONDS).getValue());
        } finally {
            complete.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
