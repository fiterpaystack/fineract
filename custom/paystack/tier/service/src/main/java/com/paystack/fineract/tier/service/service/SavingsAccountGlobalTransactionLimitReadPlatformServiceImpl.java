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

import com.paystack.fineract.tier.service.data.SavingsAccountTransactionLimitsSettingData;
import com.paystack.fineract.tier.service.data.SavingsClientClassificationLimitMappingData;
import com.paystack.fineract.tier.service.data.TransactionLimitData;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
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
                    + " stls.max_single_deposit_amount as maxSingleDepositAmount, stls.balance_cumulative as balanceCumulative "
                    + " FROM m_savings_global_transaction_limits_setting stls ";
        }

        @Override
        public SavingsAccountTransactionLimitsSettingData mapRow(ResultSet rs, int rowNum) throws SQLException {

            final Long id = rs.getLong("id");
            final String name = rs.getString("name");
            final String description = rs.getString("description");
            final Boolean isActive = rs.getBoolean("isActive");
            final BigDecimal maxSingleDepositAmount = rs.getBigDecimal("maxSingleDepositAmount");
            final BigDecimal balanceCumulative = rs.getBigDecimal("balanceCumulative");

            return SavingsAccountTransactionLimitsSettingData.builder().id(id).name(name).description(description).isActive(isActive)
                    .maxSingleDepositAmount(maxSingleDepositAmount).balanceCumulative(balanceCumulative).build();
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
                builder.append(", stls.max_single_deposit_amount as maxSingleDepositAmount,")
                        .append("stls.balance_cumulative as balanceCumulative ");
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

                final BigDecimal maxSingleDepositAmount = rs.getBigDecimal("maxSingleDepositAmount");
                final BigDecimal balanceCumulative = rs.getBigDecimal("balanceCumulative");

                builder.limits(TransactionLimitData.builder().maxSingleDepositAmount(maxSingleDepositAmount)
                        .balanceCumulative(balanceCumulative).build());
            }

            return builder.build();

        }
    }
}
