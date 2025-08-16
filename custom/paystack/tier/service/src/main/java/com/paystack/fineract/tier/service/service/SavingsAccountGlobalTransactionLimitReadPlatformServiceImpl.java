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

package com.paystack.fineract.tier.service.service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Optional;

import com.paystack.fineract.tier.service.data.SavingsAccountTransactionLimitsSettingData;
import com.paystack.fineract.tier.service.data.SavingsClientClassificationLimitMappingData;
import com.paystack.fineract.tier.service.data.TransactionLimitData;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SavingsAccountGlobalTransactionLimitReadPlatformServiceImpl
        implements SavingsAccountGlobalTransactionLimitReadPlatformService {

    private final SavingsAccountTransactionLimitSettingMapper savingsAccountTransactionLimitSettingMapper = new SavingsAccountTransactionLimitSettingMapper();
    private final ClassificationLimitMappingMapper classificationLimitMappingMapper = new ClassificationLimitMappingMapper();
    private final JdbcTemplate jdbcTemplate;

    @Override
    public SavingsAccountTransactionLimitsSettingData retrieveOne(Long id) {

        final String query = "select " + savingsAccountTransactionLimitSettingMapper.schema() + " where stls.id = ?";
        return this.jdbcTemplate.queryForObject(query, savingsAccountTransactionLimitSettingMapper, id);

    }

    @Override
    public Optional<SavingsAccountTransactionLimitsSettingData> retrieveGlobalSetting(boolean isMerchant) {
        final String query = "select " + savingsAccountTransactionLimitSettingMapper.schema()
                + " where stls.is_global_limit = ? and stls.is_active = ? and is_merchant_limit = ?";
        try {
            return Optional.ofNullable(
                    this.jdbcTemplate.queryForObject(query, savingsAccountTransactionLimitSettingMapper, true, true, isMerchant));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }

    }

    @Override
    public Collection<SavingsAccountTransactionLimitsSettingData> retrieveAll() {
        final String query = "select " + savingsAccountTransactionLimitSettingMapper.schema();
        return this.jdbcTemplate.query(query, savingsAccountTransactionLimitSettingMapper);
    }

    @Override
    public Collection<SavingsClientClassificationLimitMappingData> getLimitClassificationMappings() {
        final String query = "select " + classificationLimitMappingMapper.schema(false);
        return this.jdbcTemplate.query(query, classificationLimitMappingMapper);

    }

    @Override
    public SavingsClientClassificationLimitMappingData retrieveOneByCodeValueId(Long codeValueId) {
        final String query = "select " + classificationLimitMappingMapper.schema(true) + " and cclm.code_value_id = ?";
        return this.jdbcTemplate.queryForObject(query, classificationLimitMappingMapper, codeValueId);
    }

    @Override
    public Collection<SavingsAccountTransactionLimitsSettingData> searchForGlobalSettingByName(String name) {

        final String query = "select " + savingsAccountTransactionLimitSettingMapper.schema()
                + " where stls.is_active = ? and stls.name  like ?  limit 10";

        return jdbcTemplate.query(query, savingsAccountTransactionLimitSettingMapper, true, "%" + name + "%");
    }

    private static final class SavingsAccountTransactionLimitSettingMapper
            implements RowMapper<SavingsAccountTransactionLimitsSettingData> {

        public String schema() {

            return " stls.id, stls.name, stls.description, stls.is_active as isActive, "
                    + " stls.is_global_limit as isGlobalLimit,stls.is_merchant_limit as isMerchantLimit, stls.max_single_withdrawal_amount as maxSingleWithdrawalAmount, "
                    + " stls.max_single_deposit_amount as maxSingleDepositAmount, stls.max_daily_withdrawal_amount as maxDailyWithdrawalAmount, "
                    + " stls.balance_cumulative as balanceCumulative, stls.max_client_specific_daily_withdrawal_amount as maxClientSpecificDailyWithdrawalAmount, "
                    + " stls.max_client_specific_single_withdrawal_amount as maxClientSpecificSingleWithdrawalAmount "
                    + " FROM m_savings_global_transaction_limits_setting stls ";
        }

        @Override
        public SavingsAccountTransactionLimitsSettingData mapRow(ResultSet rs, int rowNum) throws SQLException {

            final Long id = rs.getLong("id");
            final String name = rs.getString("name");
            final String description = rs.getString("description");
            final Boolean isActive = rs.getBoolean("isActive");
            final Boolean isGlobalLimit = rs.getBoolean("isGlobalLimit");
            final Boolean isMerchantLimit = rs.getBoolean("isMerchantLimit");
            final BigDecimal maxSingleWithdrawalAmount = rs.getBigDecimal("maxSingleWithdrawalAmount");
            final BigDecimal maxSingleDepositAmount = rs.getBigDecimal("maxSingleDepositAmount");
            final BigDecimal maxDailyWithdrawalAmount = rs.getBigDecimal("maxDailyWithdrawalAmount");
            final BigDecimal balanceCumulative = rs.getBigDecimal("balanceCumulative");
            final BigDecimal maxClientSpecificDailyWithdrawalAmount = rs.getBigDecimal("maxClientSpecificDailyWithdrawalAmount");
            final BigDecimal maxClientSpecificSingleWithdrawalAmount = rs.getBigDecimal("maxClientSpecificSingleWithdrawalAmount");

            return SavingsAccountTransactionLimitsSettingData.builder().id(id).name(name).description(description).isActive(isActive)
                    .isGlobalLimit(isGlobalLimit).maxSingleWithdrawalAmount(maxSingleWithdrawalAmount)
                    .maxSingleDepositAmount(maxSingleDepositAmount).maxDailyWithdrawalAmount(maxDailyWithdrawalAmount)
                    .balanceCumulative(balanceCumulative).maxClientSpecificDailyWithdrawalAmount(maxClientSpecificDailyWithdrawalAmount)
                    .maxClientSpecificSingleWithdrawalAmount(maxClientSpecificSingleWithdrawalAmount).isMerchantLimit(isMerchantLimit)
                    .build();
        }
    }

    private static final class ClassificationLimitMappingMapper implements RowMapper<SavingsClientClassificationLimitMappingData> {

        boolean returnLimits = false;

        public String schema(boolean returnLimits) {
            this.returnLimits = returnLimits;

            StringBuilder builder = new StringBuilder(10);
            builder.append(" cclm.id,cv.id as classificationId, cclm.limit_id as limitId,")
                    .append("cv.code_value as classificationName, stls.name as limitName ");

            if (returnLimits) {
                builder.append(", stls.max_single_withdrawal_amount as maxSingleWithdrawalAmount,")
                        .append("stls.max_single_deposit_amount as maxSingleDepositAmount,")
                        .append("stls.max_daily_withdrawal_amount as maxDailyWithdrawalAmount,")
                        .append("stls.max_on_hold_amount as maxOnHoldAmount,")
                        .append("stls.max_client_specific_daily_withdrawal_amount as maxClientSpecificDailyWithdrawalAmount,")
                        .append("stls.max_client_specific_single_withdrawal_amount as maxClientSpecificSingleWithdrawalAmount ");
            }
            builder.append(" FROM m_code_value cv inner join m_code mc on mc.id = cv.code_id  ")
                    .append(" left join m_client_classification_limit_mapping cclm on cclm.code_value_id = cv.id ")
                    .append(" left join m_savings_global_transaction_limits_setting stls on stls.id = cclm.limit_id ");

            builder.append(" where mc.code_name = 'ClientClassification'");

            return builder.toString();

        }

        @Override
        public SavingsClientClassificationLimitMappingData mapRow(ResultSet rs, int rowNum) throws SQLException {

            final Long id = JdbcSupport.getLong(rs, "id");
            final Long classificationId = JdbcSupport.getLong(rs, "classificationId");
            final Long limitId = JdbcSupport.getLong(rs, "limitId");
            final String classificationName = rs.getString("classificationName");
            final String limitName = rs.getString("limitName");
            // Generate the mapper data
            SavingsClientClassificationLimitMappingData.SavingsClientClassificationLimitMappingDataBuilder builder = SavingsClientClassificationLimitMappingData
                    .builder().id(id).classificationId(classificationId).limitId(limitId).classificationName(classificationName)
                    .limitName(limitName);

            if (returnLimits) {

                final BigDecimal maxSingleWithdrawalAmount = rs.getBigDecimal("maxSingleWithdrawalAmount");
                final BigDecimal maxSingleDepositAmount = rs.getBigDecimal("maxSingleDepositAmount");
                final BigDecimal maxDailyWithdrawalAmount = rs.getBigDecimal("maxDailyWithdrawalAmount");
                final BigDecimal maxOnHoldAmount = rs.getBigDecimal("maxOnHoldAmount");
                final BigDecimal maxClientSpecificDailyWithdrawalAmount = rs.getBigDecimal("maxClientSpecificDailyWithdrawalAmount");
                final BigDecimal maxClientSpecificSingleWithdrawalAmount = rs.getBigDecimal("maxClientSpecificSingleWithdrawalAmount");

                builder.limits(TransactionLimitData.builder().maxSingleWithdrawalAmount(maxSingleWithdrawalAmount)
                        .maxSingleDepositAmount(maxSingleDepositAmount).maxDailyWithdrawalAmount(maxDailyWithdrawalAmount)
                        .maxOnHoldAmount(maxOnHoldAmount).build())
                        .maxClientSpecificDailyWithdrawalAmount(maxClientSpecificDailyWithdrawalAmount)
                        .maxClientSpecificSingleWithdrawalAmount(maxClientSpecificSingleWithdrawalAmount);
            }

            return builder.build();

        }
    }
}
