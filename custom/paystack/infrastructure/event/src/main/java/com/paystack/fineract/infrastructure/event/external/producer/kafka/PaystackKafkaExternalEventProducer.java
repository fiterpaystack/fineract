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
package com.paystack.fineract.infrastructure.event.external.producer.kafka;

import static org.apache.fineract.infrastructure.core.diagnostics.performance.MeasuringUtil.measure;

import com.paystack.fineract.infrastructure.event.external.producer.PaystackExternalEventProducer;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.event.external.exception.AcknowledgementTimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
@ConditionalOnProperty(value = "fineract.events.external.producer.kafka.enabled", havingValue = "true")
public class PaystackKafkaExternalEventProducer implements PaystackExternalEventProducer {

    @Autowired
    protected KafkaTemplate<String, String> paystackExternalEventsKafkaTemplate;

    @Autowired
    protected FineractProperties fineractProperties;

    @Override
    @Transactional
    public void sendEvents(String messageJson) throws AcknowledgementTimeoutException {
        measure(() -> {
            final FineractProperties.FineractExternalEventsProducerKafkaProperties kafkaProperties = fineractProperties.getEvents()
                    .getExternal().getProducer().getKafka();
            final String topicName = kafkaProperties.getTopic().getName();
            final String headerKeyValue = System.currentTimeMillis() + "-" + UUID.randomUUID();

            final Message<String> kafkaMessage = MessageBuilder.withPayload(messageJson).setHeader(KafkaHeaders.TOPIC, topicName)
                    .setHeader(KafkaHeaders.KEY, headerKeyValue).build();

            try {
                CompletableFuture<SendResult<String, String>> sendResult = paystackExternalEventsKafkaTemplate.send(kafkaMessage);
                sendResult.get(kafkaProperties.getTimeoutInSeconds(), TimeUnit.SECONDS);

                if (log.isDebugEnabled()) {
                    log.debug("Successfully sent Kafka message to topic: {} with key: {}", topicName, headerKeyValue);
                }

            } catch (java.util.concurrent.TimeoutException e) {
                log.error("Timeout while sending message to Kafka topic: {} with key: {}", topicName, headerKeyValue);
                throw new AcknowledgementTimeoutException("Timeout while waiting for Kafka acknowledgement", e);
            } catch (java.util.concurrent.ExecutionException e) {
                log.error("Execution error while sending message to Kafka topic: {} with key: {}", topicName, headerKeyValue, e);
                throw new RuntimeException("Failed to send message to Kafka: " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while sending message to Kafka topic: {} with key: {}", topicName, headerKeyValue);
                throw new RuntimeException("Interrupted while sending message to Kafka: " + e.getMessage(), e);
            } catch (Exception e) {
                log.error("Unexpected error while sending message to Kafka topic: {} with key: {}", topicName, headerKeyValue, e);
                throw new RuntimeException("Unexpected error while sending message to Kafka: " + e.getMessage(), e);
            }
        }, timeTaken -> {
            if (log.isDebugEnabled()) {
                log.debug("Kafka message send operation completed in {} ms", timeTaken.toMillis());
            }
        });
    }
}
