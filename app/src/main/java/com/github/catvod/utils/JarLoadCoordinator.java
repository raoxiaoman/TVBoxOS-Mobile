package com.github.catvod.utils;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public final class JarLoadCoordinator<T> {

    private final ConcurrentHashMap<String, FutureTask<Result<T>>> tasks = new ConcurrentHashMap<>();

    public Result<T> getOrLoad(String key, Callable<Result<T>> loader) throws InterruptedException, ExecutionException {
        for (; ; ) {
            FutureTask<Result<T>> candidate = new FutureTask<>(loader);
            FutureTask<Result<T>> task = tasks.putIfAbsent(key, candidate);
            boolean created = task == null;
            if (created) {
                task = candidate;
                candidate.run();
            }
            boolean completedWhenAcquired = !created && task.isDone();

            try {
                Result<T> result = task.get();
                if (result.isRetryableFailure()) {
                    tasks.remove(key, task);
                    if (completedWhenAcquired) {
                        continue;
                    }
                }
                return result;
            } catch (ExecutionException e) {
                tasks.remove(key, task);
                if (completedWhenAcquired) {
                    continue;
                }
                throw e;
            }
        }
    }

    public boolean hasTerminalResult(String key) {
        FutureTask<Result<T>> task = tasks.get(key);
        return task != null && task.isDone();
    }

    public void clear() {
        tasks.clear();
    }

    public static final class Result<T> {

        private enum Status {
            SUCCESS,
            INCOMPATIBLE,
            RETRYABLE_FAILURE
        }

        private final Status status;
        private final T value;

        private Result(Status status, T value) {
            this.status = status;
            this.value = value;
        }

        public static <T> Result<T> success(T value) {
            return new Result<>(Status.SUCCESS, Objects.requireNonNull(value));
        }

        public static <T> Result<T> incompatible() {
            return new Result<>(Status.INCOMPATIBLE, null);
        }

        public static <T> Result<T> retryableFailure() {
            return new Result<>(Status.RETRYABLE_FAILURE, null);
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

        public T getValue() {
            return value;
        }
    }
}
