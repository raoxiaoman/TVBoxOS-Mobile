package com.github.catvod.utils;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/**
 * Coordinates preparation of one on-disk dynamic-code cache file across all loaders.
 * Results are deliberately not retained after completion: each loader still validates
 * its own checksum and creates its own runtime class loader.
 */
public final class FileLoadCoordinator {

    private static final ConcurrentHashMap<String, FutureTask<Result>> TASKS = new ConcurrentHashMap<>();

    public Result getOrLoad(File target, Callable<Result> loader) throws InterruptedException, ExecutionException {
        String key = target.getAbsoluteFile().getPath();
        for (; ; ) {
            FutureTask<Result> candidate = new FutureTask<>(loader);
            FutureTask<Result> task = TASKS.putIfAbsent(key, candidate);
            if (task == null) {
                task = candidate;
                try {
                    candidate.run();
                } finally {
                    TASKS.remove(key, candidate);
                }
            }
            try {
                return task.get();
            } catch (ExecutionException e) {
                TASKS.remove(key, task);
                throw e;
            }
        }
    }

    public static final class Result {

        private enum Status {
            SUCCESS,
            INCOMPATIBLE,
            RETRYABLE_FAILURE
        }

        private final Status status;

        private Result(Status status) {
            this.status = status;
        }

        public static Result success() {
            return new Result(Status.SUCCESS);
        }

        public static Result incompatible() {
            return new Result(Status.INCOMPATIBLE);
        }

        public static Result retryableFailure() {
            return new Result(Status.RETRYABLE_FAILURE);
        }

        public boolean isSuccess() {
            return status == Status.SUCCESS;
        }

        public boolean isIncompatible() {
            return status == Status.INCOMPATIBLE;
        }

        public boolean isRetryableFailure() {
            return status == Status.RETRYABLE_FAILURE;
        }
    }
}
