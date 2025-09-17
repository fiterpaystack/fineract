/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.paystack.fineract.infrastructure.config;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Configuration for asynchronous Kafka notification processing.
 */
@AutoConfiguration
@EnableAsync
@ComponentScan(basePackages = "com.paystack.fineract.infrastructure.event")
@ConditionalOnProperty(value = "fineract.events.external.producer.kafka.enabled", havingValue = "true")
public class PaystackKafkaAsyncConfiguration {

    @Value("${fineract.events.external.producer.kafka.async.core-pool-size:5}")
    private int corePoolSize;

    @Value("${fineract.events.external.producer.kafka.async.max-pool-size:20}")
    private int maxPoolSize;

    @Value("${fineract.events.external.producer.kafka.async.queue-capacity:100}")
    private int queueCapacity;

    @Value("${fineract.events.external.producer.kafka.async.keep-alive-seconds:60}")
    private int keepAliveSeconds;

    @Value("${fineract.events.external.producer.kafka.async.thread-name-prefix:paystack-kafka-notification-}")
    private String threadNamePrefix;

    @Value("${fineract.events.external.producer.kafka.async.max-retries:3}")
    private int maxRetries;

    /**
     * Creates a dedicated thread pool executor for Kafka notification processing.
     */
    @Bean(name = "kafkaNotificationExecutor")
    public Executor kafkaNotificationExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(corePoolSize, maxPoolSize, keepAliveSeconds, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity), r -> {
                    Thread thread = new Thread(r);
                    thread.setName(threadNamePrefix + System.currentTimeMillis());
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.CallerRunsPolicy() // Fallback to caller thread if queue is full
        );

        // Allow core threads to timeout
        executor.allowCoreThreadTimeOut(true);

        return executor;
    }

    /**
     * Provides the maximum retries configuration value.
     */
    @Bean(name = "kafkaNotificationMaxRetries")
    public Integer kafkaNotificationMaxRetries() {
        return maxRetries;
    }
}
