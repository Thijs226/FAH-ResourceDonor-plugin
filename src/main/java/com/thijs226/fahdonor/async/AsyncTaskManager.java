package com.thijs226.fahdonor.async;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Manages asynchronous operations for the FAH plugin with proper thread pooling
 * and error handling. Provides utilities for running tasks async and syncing
 * back to the main thread when needed.
 */
public class AsyncTaskManager {
    
    private final Plugin plugin;
    private final ExecutorService executor;
    private final AtomicInteger taskCounter = new AtomicInteger(0);
    
    public AsyncTaskManager(Plugin plugin, int threadPoolSize) {
        this.plugin = plugin;
        this.executor = Executors.newFixedThreadPool(
            threadPoolSize,
            new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(1);
                
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "FAH-Async-" + threadNumber.getAndIncrement());
                    thread.setDaemon(true);
                    thread.setPriority(Thread.NORM_PRIORITY - 1);
                    return thread;
                }
            }
        );
    }
    
    /**
     * Runs a task asynchronously and returns a CompletableFuture
     */
    public <T> CompletableFuture<T> runAsync(Supplier<T> task) {
        int taskId = taskCounter.incrementAndGet();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.get();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, 
                    "Error in async task #" + taskId, e);
                throw new RuntimeException(e);
            }
        }, executor);
    }
    
    /**
     * Runs a task asynchronously without return value
     */
    public CompletableFuture<Void> runAsyncVoid(Runnable task) {
        int taskId = taskCounter.incrementAndGet();
        return CompletableFuture.runAsync(() -> {
            try {
                task.run();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, 
                    "Error in async task #" + taskId, e);
            }
        }, executor);
    }
    
    /**
     * Runs a task asynchronously then executes callback on main thread
     */
    public <T> void runAsyncThenSync(Supplier<T> asyncTask, Consumer<T> syncCallback) {
        runAsync(asyncTask).thenAccept(result -> {
            if (Bukkit.isPrimaryThread()) {
                syncCallback.accept(result);
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> syncCallback.accept(result));
            }
        });
    }
    
    /**
     * Runs a task on the main thread if not already on it
     */
    public void runSync(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
    
    /**
     * Runs a task on the main thread after a delay
     */
    public void runSyncLater(Runnable task, long delayTicks) {
        Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
    }
    
    /**
     * Runs a task repeatedly on async thread
     */
    public void runAsyncRepeating(Runnable task, long initialDelayTicks, long periodTicks) {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, initialDelayTicks, periodTicks);
    }
    
    /**
     * Shuts down the async task manager gracefully
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Gets the number of tasks that have been submitted
     */
    public int getTaskCount() {
        return taskCounter.get();
    }
}
