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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.hooks.domain.Hook;
import org.apache.fineract.infrastructure.hooks.domain.HookTemplate;
import org.apache.fineract.infrastructure.hooks.processor.HookProcessor;
import org.apache.fineract.infrastructure.hooks.processor.WebHookProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationContext;

/**
 * Unit tests for PaystackHookProcessorProvider.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaystackHookProcessorProviderTest {

    @Mock
    private ApplicationContext applicationContext;

    @InjectMocks
    private PaystackHookProcessorProvider provider;

    private Hook kafkaHook;
    private Hook webHook;
    private HookProcessor kafkaProcessor;
    private WebHookProcessor webProcessor;

    @BeforeEach
    void setUp() {
        // Create Kafka hook
        HookTemplate kafkaTemplate = mock(HookTemplate.class);
        when(kafkaTemplate.getName()).thenReturn("Kafka");
        kafkaHook = mock(Hook.class);
        when(kafkaHook.getTemplate()).thenReturn(kafkaTemplate);

        // Create Web hook
        HookTemplate webTemplate = mock(HookTemplate.class);
        when(webTemplate.getName()).thenReturn("Web");
        webHook = mock(Hook.class);
        when(webHook.getTemplate()).thenReturn(webTemplate);

        // Create processors
        kafkaProcessor = mock(HookProcessor.class);
        webProcessor = mock(WebHookProcessor.class);

        // Setup application context
        when(applicationContext.getBean("kafkaHookProcessor", HookProcessor.class)).thenReturn(kafkaProcessor);
    }

    @Test
    void shouldReturnKafkaProcessorForKafkaTemplate() {
        // Given
        when(applicationContext.getBean("kafkaHookProcessor", HookProcessor.class)).thenReturn(kafkaProcessor);

        // When
        HookProcessor result = provider.getProcessor(kafkaHook);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(kafkaProcessor);
        verify(applicationContext).getBean("kafkaHookProcessor", HookProcessor.class);
    }

    @Test
    void shouldReturnKafkaProcessorForKafkaTemplateCaseInsensitive() {
        // Given
        HookTemplate kafkaTemplateLower = mock(HookTemplate.class);
        when(kafkaTemplateLower.getName()).thenReturn("kafka");
        Hook kafkaHookLower = mock(Hook.class);
        when(kafkaHookLower.getTemplate()).thenReturn(kafkaTemplateLower);
        when(applicationContext.getBean("kafkaHookProcessor", HookProcessor.class)).thenReturn(kafkaProcessor);

        // When
        HookProcessor result = provider.getProcessor(kafkaHookLower);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(kafkaProcessor);
    }

    @Test
    void shouldDelegateToParentForNonKafkaTemplate() {
        // Given
        // For "Web" template, parent should return webHookProcessor
        // Parent uses exact match (equals) for Web template
        when(applicationContext.getBean("kafkaHookProcessor", HookProcessor.class)).thenReturn(kafkaProcessor);
        // Mock parent's behavior - for "Web" template, parent returns webHookProcessor
        when(applicationContext.getBean("webHookProcessor", WebHookProcessor.class))
                .thenReturn(webProcessor);

        // When
        HookProcessor result = provider.getProcessor(webHook);

        // Then
        // Should not return Kafka processor for non-Kafka template
        assertThat(result).isNotEqualTo(kafkaProcessor);
        // Parent should return webProcessor for Web template
        assertThat(result).isEqualTo(webProcessor);
        verify(applicationContext).getBean("webHookProcessor", WebHookProcessor.class);
    }
}
