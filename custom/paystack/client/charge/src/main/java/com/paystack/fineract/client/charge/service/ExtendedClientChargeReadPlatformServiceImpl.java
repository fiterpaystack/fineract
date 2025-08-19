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
        final String sql = "SELECT c.id AS chargeId, c.name AS name FROM m_charge c where lower(c.name) like :name ORDER BY c.name LIMIT 10";
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
        String sql = "select " + chargeMapper.chargeSchema() + " inner join m_client_charge_override o on o.charge_id = c.id "
                + " where c.is_deleted=false and o.id = :id";
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

        String baseQuery = chargeMapper.chargeSchema() + " inner join m_client_charge_override o on o.charge_id = c.id ";

        String where = " where c.is_deleted=false and o.client_id = :clientId ";

        String countSql = "select count(*)" + baseQuery + where;
        String listSql = "select " + baseQuery + where + " order by c.name limit :limit offset :offset";

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

            return ClientChargeResult.builder().chargeOverrideId(JdbcSupport.getLong(rs, "overrideId"))
                    .clientId(JdbcSupport.getLong(rs, "clientId")).chargeId(JdbcSupport.getLong(rs, "chargeId")).chargeData(chargeData)
                    .build();

        }

    }
}
