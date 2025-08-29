/*
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

package com.paystack.fineract.client.charge.service;

import com.paystack.fineract.client.charge.dto.ChargeSearchResult;
import com.paystack.fineract.client.charge.dto.ClientChargeResult;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.apache.fineract.accounting.common.AccountingDropdownReadPlatformService;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainServiceJpa;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.entityaccess.service.FineractEntityAccessUtil;
import org.apache.fineract.organisation.monetary.service.CurrencyReadPlatformService;
import org.apache.fineract.portfolio.charge.data.ChargeData;
import org.apache.fineract.portfolio.charge.service.ChargeDropdownReadPlatformService;
import org.apache.fineract.portfolio.charge.service.ChargeReadPlatformServiceImpl;
import org.apache.fineract.portfolio.common.service.DropdownReadPlatformService;
import org.apache.fineract.portfolio.tax.service.TaxReadPlatformService;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ExtendedClientChargeReadPlatformServiceImpl extends ChargeReadPlatformServiceImpl
        implements ExtendedClientChargeReadPlatformService {

    private static final int DEFAULT_LIMIT = 100;
    private static final int DEFAULT_OFFSET = 0;
    private final NamedParameterJdbcTemplate npJdbcTemplate;
    private final ExtRowMapper rowMapper = new ExtRowMapper();
    private static final ChargeReadPlatformServiceImpl.ChargeMapper chargeMapper = new ChargeReadPlatformServiceImpl.ChargeMapper();

    public ExtendedClientChargeReadPlatformServiceImpl(CurrencyReadPlatformService currencyReadPlatformService,
            ChargeDropdownReadPlatformService chargeDropdownReadPlatformService, JdbcTemplate jdbcTemplate,
            DropdownReadPlatformService dropdownReadPlatformService, FineractEntityAccessUtil fineractEntityAccessUtil,
            AccountingDropdownReadPlatformService accountingDropdownReadPlatformService, TaxReadPlatformService taxReadPlatformService,
            ConfigurationDomainServiceJpa configurationDomainServiceJpa, NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        super(currencyReadPlatformService, chargeDropdownReadPlatformService, jdbcTemplate, dropdownReadPlatformService,
                fineractEntityAccessUtil, accountingDropdownReadPlatformService, taxReadPlatformService, configurationDomainServiceJpa,
                namedParameterJdbcTemplate);
        this.npJdbcTemplate = namedParameterJdbcTemplate;
    }

    @Override
    public List<ChargeSearchResult> retrieveTemplateCharges(String searchKeywords) {
        final String sql = "SELECT c.id AS chargeId, c.name AS name FROM m_charge c where c.is_active = true and c.is_deleted = false and lower(c.name) like :name ORDER BY c.name LIMIT 10";
        final String likeSearch = "%" + searchKeywords.toLowerCase() + "%";

        return npJdbcTemplate.query(sql, Map.of("name", likeSearch), (ResultSet rs) -> {
            List<ChargeSearchResult> results = new java.util.ArrayList<>();
            while (rs.next()) {
                ChargeSearchResult result = new ChargeSearchResult();
                result.setChargeId(rs.getLong("chargeId"));
                result.setName(rs.getString("name"));
                results.add(result);
            }
            return results;
        });
    }

    @Override
    public ClientChargeResult get(Long id) {
        // Single override by override id
        String sql = "select " + this.schema() + this.fromClause() + " where c.is_deleted=false and o.id = :id";
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", id);
        try {
            return this.npJdbcTemplate.queryForObject(sql, params, rowMapper::mapRow);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public Page<ClientChargeResult> listByClient(Long clientId, Integer limit, Integer offset) {
        int lim = (limit == null || limit <= 0) ? DEFAULT_LIMIT : limit;
        int off = (offset == null || offset < 0) ? DEFAULT_OFFSET : offset;

        String baseQuery = this.schema() + this.fromClause();

        String where = " where c.is_deleted=false and o.client_id = :clientId ";

        String countSql = "select count(*) " + fromClause() + where;
        String listSql = "select " + baseQuery + where + " order by c.name limit :limit offset :offset ";

        MapSqlParameterSource params = new MapSqlParameterSource().addValue("clientId", clientId).addValue("limit", lim).addValue("offset",
                off);

        Integer total = npJdbcTemplate.queryForObject(countSql, params, Integer.class);
        List<ClientChargeResult> items = npJdbcTemplate.query(listSql, params, rowMapper::mapRow);
        return new Page<>(items, total == null ? 0 : total);
    }

    private static final class ExtRowMapper implements RowMapper<ClientChargeResult> {

        @Override
        public ClientChargeResult mapRow(ResultSet rs, int rowNum) throws SQLException {
            ChargeData chargeData = chargeMapper.mapRow(rs, rowNum);

            return ClientChargeResult.builder().overrideId(JdbcSupport.getLong(rs, "overrideId"))
                    .overrideMaxCap(JdbcSupport.getBigDecimalDefaultToNullIfZero(rs, "overrideMaxCap"))
                    .overrideMinCap(JdbcSupport.getBigDecimalDefaultToNullIfZero(rs, "overrideMinCap"))
                    .overrideAmount(JdbcSupport.getBigDecimalDefaultToNullIfZero(rs, "overrideAmount"))
                    .overrideActive(rs.getBoolean("overrideActive")).clientId(JdbcSupport.getLong(rs, "clientId")).chargeData(chargeData)
                    .build();

        }

    }

    private String schema() {
        return " o.id as overrideId,o.client_id as clientId, o.min_cap as overrideMinCap, o.max_cap as overrideMaxCap, o.is_active as overrideActive,o.amount as overrideAmount, "
                + " c.id as id, c.name as name, c.amount as amount, c.currency_code as currencyCode, "
                + "c.charge_applies_to_enum as chargeAppliesTo, c.charge_time_enum as chargeTime, "
                + "c.charge_payment_mode_enum as chargePaymentMode, "
                + "c.charge_calculation_enum as chargeCalculation, c.is_penalty as penalty, "
                + "c.is_active as active, c.is_free_withdrawal as isFreeWithdrawal, c.free_withdrawal_charge_frequency as freeWithdrawalChargeFrequency, c.restart_frequency as restartFrequency, c.restart_frequency_enum as restartFrequencyEnum,"
                + "oc.name as currencyName, oc.decimal_places as currencyDecimalPlaces, "
                + "oc.currency_multiplesof as inMultiplesOf, oc.display_symbol as currencyDisplaySymbol, "
                + "oc.internationalized_name_code as currencyNameCode, c.fee_on_day as feeOnDay, c.fee_on_month as feeOnMonth, "
                + "c.fee_interval as feeInterval, c.fee_frequency as feeFrequency,c.min_cap as minCap,c.max_cap as maxCap, "
                + "c.income_or_liability_account_id as glAccountId , acc.name as glAccountName, acc.gl_code as glCode, "
                + "tg.id as taxGroupId, c.is_payment_type as isPaymentType, pt.id as paymentTypeId, pt.value as paymentTypeName, tg.name as taxGroupName, "
                + "c.enable_fee_split as enableFeeSplit ";
    }

    private String fromClause() {
        return " from m_charge c " + "join m_organisation_currency oc on c.currency_code = oc.code "
                + " LEFT JOIN acc_gl_account acc on acc.id = c.income_or_liability_account_id "
                + " LEFT JOIN m_tax_group tg on tg.id = c.tax_group_id " + " LEFT JOIN m_payment_type pt on pt.id = c.payment_type_id "
                + " inner join m_client_charge_override o on o.charge_id = c.id ";
    }
}
