package com.paystack.fineract.portfolio.savings.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configuration for asynchronous processing in savings operations. This enables async processing for charge cascade
 * operations.
 */
@Configuration
@EnableAsync
public class PaystackSavingsAsyncConfig {

    /**
     * Creates a thread pool executor for savings-related async operations. This executor is specifically tuned for
     * charge cascade operations.
     */
    @Bean(name = "savingsAsyncExecutor")
    public Executor savingsAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Core pool size - minimum number of threads
        executor.setCorePoolSize(5);

        // Maximum pool size - maximum number of threads
        executor.setMaxPoolSize(20);

        // Queue capacity - number of tasks that can be queued
        executor.setQueueCapacity(100);

        // Thread name prefix for easier debugging
        executor.setThreadNamePrefix("SavingsAsync-");

        // Rejection policy - what to do when queue is full
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // Allow core threads to timeout
        executor.setAllowCoreThreadTimeOut(true);

        // Keep alive time for idle threads (in seconds)
        executor.setKeepAliveSeconds(60);

        // Wait for tasks to complete on shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);

        // Maximum time to wait for tasks to complete (in seconds)
        executor.setAwaitTerminationSeconds(300);

        executor.initialize();
        return executor;
    }
}
