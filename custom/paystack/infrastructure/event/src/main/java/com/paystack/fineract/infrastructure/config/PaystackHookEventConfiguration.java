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
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration for Kafka hook event processing. This configuration is always enabled to support hook event retry
 * functionality.
 */
@AutoConfiguration
@EnableScheduling
public class PaystackHookEventConfiguration {

    /**
     * Creates a dedicated thread pool executor for Kafka hook event retry processing. This executor is used by
     * HookEventRetryService for asynchronous retry operations.
     */
    @Bean(name = "kafkaHookRetryExecutor")
    public Executor kafkaHookRetryExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(3, 10, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(50), r -> {
            Thread thread = new Thread(r);
            thread.setName("paystack-kafka-hook-retry-" + System.currentTimeMillis());
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.CallerRunsPolicy());

        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
}
