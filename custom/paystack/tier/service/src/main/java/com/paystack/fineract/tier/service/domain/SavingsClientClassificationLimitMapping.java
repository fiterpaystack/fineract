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
package com.paystack.fineract.tier.service.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.apache.fineract.infrastructure.codes.domain.CodeValue;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

@Getter
@Setter
@Entity
@Table(name = "m_client_classification_limit_mapping")
public class SavingsClientClassificationLimitMapping extends AbstractPersistableCustom<Integer> {

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "limit_id", referencedColumnName = "id", nullable = false)
    private SavingsAccountGlobalTransactionLimitSetting savingsAccountGlobalTransactionLimitSetting;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "code_value_id", referencedColumnName = "id", nullable = false)
    private CodeValue classification;

    public SavingsClientClassificationLimitMapping() {
        // TODO Auto-generated constructor stub
    }

    public SavingsClientClassificationLimitMapping(SavingsAccountGlobalTransactionLimitSetting savingsAccountGlobalTransactionLimitSetting,
            CodeValue classification) {
        this.savingsAccountGlobalTransactionLimitSetting = savingsAccountGlobalTransactionLimitSetting;
        this.classification = classification;
    }

}
