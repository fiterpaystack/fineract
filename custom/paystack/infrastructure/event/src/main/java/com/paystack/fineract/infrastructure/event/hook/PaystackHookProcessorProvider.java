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
package com.paystack.fineract.infrastructure.event.hook;

import org.apache.fineract.infrastructure.hooks.domain.Hook;
import org.apache.fineract.infrastructure.hooks.processor.HookProcessor;
import org.apache.fineract.infrastructure.hooks.processor.HookProcessorProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Extended hook processor provider that recognizes Kafka template.
 *
 * This extends the default HookProcessorProvider to add support for the Kafka hook template while delegating to parent
 * logic for other templates (Web, SMS, ElasticSearch, etc.).
 */
@Service
@Primary
public class PaystackHookProcessorProvider extends HookProcessorProvider {

    private static final String KAFKA_TEMPLATE_NAME = "Kafka";

    private final ApplicationContext applicationContext;

    public PaystackHookProcessorProvider(ApplicationContext applicationContext) {
        super(applicationContext);
        this.applicationContext = applicationContext;
    }

    @Override
    public HookProcessor getProcessor(final Hook hook) {
        final String templateName = hook.getTemplate().getName();

        // Handle Kafka template (our custom addition)
        if (KAFKA_TEMPLATE_NAME.equalsIgnoreCase(templateName)) {
            return applicationContext.getBean("kafkaHookProcessor", HookProcessor.class);
        }

        // Delegate to parent for other templates (Web, SMS, ElasticSearch, etc.)
        return super.getProcessor(hook);
    }
}
