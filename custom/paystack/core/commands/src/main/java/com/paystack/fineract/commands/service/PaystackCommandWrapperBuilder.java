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
package com.paystack.fineract.commands.service;

import org.apache.fineract.commands.service.CommandWrapperBuilder;

public class PaystackCommandWrapperBuilder extends CommandWrapperBuilder {

    public PaystackCommandWrapperBuilder withJson(String withJson) {
        return (PaystackCommandWrapperBuilder) super.withJson(withJson);
    }

    public CommandWrapperBuilder retryKafkaNotification(final Long id) {
        this.actionName = "RETRY";
        this.entityName = "KAFKANOTIFICATION";
        this.entityId = id;
        this.href = "/v1/kafka/notifications/" + id + "/retry";
        return this;
    }

    public CommandWrapperBuilder retryKafkaNotificationsByAccount(final Long accountId) {
        this.actionName = "RETRY_BY_ACCOUNT";
        this.entityName = "KAFKANOTIFICATION";
        this.entityId = accountId;
        this.href = "/v1/kafka/notifications/accounts/" + accountId + "/retry";
        return this;
    }

    public CommandWrapperBuilder retryAllKafkaNotifications() {
        this.actionName = "RETRY_ALL";
        this.entityName = "KAFKANOTIFICATION";
        this.href = "/v1/kafka/notifications/retry-all";
        return this;
    }

    public CommandWrapperBuilder createDiscountRule() {
        this.actionName = "CREATE";
        this.entityName = "DISCOUNTRULE";
        this.entityId = null;
        this.href = "/v1/discount-rules";
        return this;
    }

    public CommandWrapperBuilder updateDiscountRule(final Long ruleId) {
        this.actionName = "UPDATE";
        this.entityName = "DISCOUNTRULE";
        this.entityId = ruleId;
        this.href = "/v1/discount-rules/" + ruleId;
        return this;
    }

    public CommandWrapperBuilder deleteDiscountRule(final Long ruleId) {
        this.actionName = "DELETE";
        this.entityName = "DISCOUNTRULE";
        this.entityId = ruleId;
        this.href = "/v1/discount-rules/" + ruleId;
        return this;
    }

    public CommandWrapperBuilder assignDiscountRuleToProduct(final Long ruleId) {
        this.actionName = "CREATE";
        this.entityName = "DISCOUNTRULE_PRODUCT_ASSIGNMENT";
        this.entityId = ruleId;
        this.href = "/v1/discount-rules/products/" + ruleId + "/assign";
        return this;
    }

    public CommandWrapperBuilder assignDiscountRuleToCharge(final Long ruleId) {
        this.actionName = "CREATE";
        this.entityName = "DISCOUNTRULE_CHARGE_ASSIGNMENT";
        this.entityId = ruleId;
        this.href = "/v1/discount-rules/charges/" + ruleId + "/assign";
        return this;
    }

    public CommandWrapperBuilder upgradeClientToEntity(final Long clientId) {
        this.actionName = "UPGRADETOENTITY";
        this.entityName = "CLIENT";
        this.entityId = clientId;
        this.clientId = clientId;
        this.href = "/clients/" + clientId + "?command=upgradeToEntity";
        return this;
    }

}
