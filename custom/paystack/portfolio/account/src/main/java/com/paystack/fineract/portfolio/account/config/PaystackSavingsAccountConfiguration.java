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
package com.paystack.fineract.portfolio.account.config;

import com.paystack.fineract.portfolio.account.repository.SavingsAccountWithdrawalFrequencySettingRepository;
import com.paystack.fineract.portfolio.account.service.PaystackSavingsAccountReadPlatformServiceImpl;
import com.paystack.fineract.portfolio.savings.repository.SavingsProductWithdrawalFrequencySettingRepository;
import org.apache.fineract.infrastructure.core.service.PaginationHelper;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.security.utils.ColumnValidator;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountAssembler;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepositoryWrapper;
import org.apache.fineract.portfolio.savings.service.SavingsAccountReadPlatformService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Configuration class for Paystack savings account services. Overrides core savings account service beans with custom
 * implementations.
 */
@Configuration
public class PaystackSavingsAccountConfiguration {

    /**
     * Override the core SavingsAccountReadPlatformService bean with Paystack implementation. This service handles all
     * savings account reading operations including withdrawal frequency settings.
     */
    @Bean
    @Primary
    public SavingsAccountReadPlatformService savingsAccountReadPlatformService(PlatformSecurityContext context, JdbcTemplate jdbcTemplate,
            SavingsAccountAssembler savingAccountAssembler, PaginationHelper paginationHelper, ColumnValidator columnValidator,
            DatabaseSpecificSQLGenerator sqlGenerator, SavingsAccountRepositoryWrapper savingsAccountRepositoryWrapper,
            SavingsAccountWithdrawalFrequencySettingRepository accountSettingRepository,
            SavingsProductWithdrawalFrequencySettingRepository productSettingRepository) {
        return new PaystackSavingsAccountReadPlatformServiceImpl(context, jdbcTemplate, savingAccountAssembler, paginationHelper,
                columnValidator, sqlGenerator, savingsAccountRepositoryWrapper, accountSettingRepository, productSettingRepository);
    }
}
