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
package com.paystack.fineract.infrastructure.event.external.domain;

import java.util.List;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository interface for the KafkaNotification entity.
 */
public interface KafkaNotificationRepository extends JpaRepository<KafkaNotification, Long>, JpaSpecificationExecutor<KafkaNotification> {

    /**
     * Find notifications by status, ordered by creation date and ID.
     */
    List<KafkaNotification> findByStatusOrderByCreatedDateAscIdAsc(KafkaNotificationStatus status, Pageable pageable);

    /**
     * Update the status of notifications to SENT.
     */
    @Modifying
    @Query("UPDATE KafkaNotification k SET k.status = :sentStatus WHERE k.id IN :ids")
    void markNotificationsSent(@Param("ids") List<Long> ids, @Param("sentStatus") KafkaNotificationStatus sentStatus);

    /**
     * Update the status of notifications to FAILED and increment the retry count.
     */
    @Modifying
    @Query("UPDATE KafkaNotification k SET k.status = :failedStatus, k.numberOfRetries = k.numberOfRetries + 1 WHERE k.id IN :ids")
    void markNotificationsFailed(@Param("ids") List<Long> ids, @Param("failedStatus") KafkaNotificationStatus failedStatus);

    /**
     * Find notifications by account.
     */
    List<KafkaNotification> findByAccount(SavingsAccount account);
}
