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
package org.apache.fineract.portfolio.tax.service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.accounting.common.AccountingDropdownReadPlatformService;
import org.apache.fineract.accounting.common.AccountingEnumerations;
import org.apache.fineract.accounting.glaccount.data.GLAccountData;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.portfolio.tax.data.TaxComponentData;
import org.apache.fineract.portfolio.tax.data.TaxComponentHistoryData;
import org.apache.fineract.portfolio.tax.data.TaxGroupData;
import org.apache.fineract.portfolio.tax.data.TaxGroupMappingsData;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

@RequiredArgsConstructor
public class TaxReadPlatformServiceImpl implements TaxReadPlatformService {

    private static final TaxComponentExtractor TAX_COMPONENT_MAPPER = new TaxComponentExtractor();
    private static final TaxGroupExtractor TAX_GROUP_MAPPER = new TaxGroupExtractor();
    private static final TaxComponentLookUpMapper TAX_COMPONENT_LOOK_UP_MAPPER = new TaxComponentLookUpMapper();
    private static final TaxGroupLookUpMapper TAX_GROUP_LOOK_UP_MAPPER = new TaxGroupLookUpMapper();

    private final JdbcTemplate jdbcTemplate;
    private final AccountingDropdownReadPlatformService accountingDropdownReadPlatformService;

    @Override
    public Collection<TaxComponentData> retrieveAllTaxComponents() {
        String sql = "select " + TAX_COMPONENT_MAPPER.getSchema();
        return this.jdbcTemplate.query(sql, TAX_COMPONENT_MAPPER); // NOSONAR
    }

    @Override
    public TaxComponentData retrieveTaxComponentData(final Long id) {
        String sql = "select " + TAX_COMPONENT_MAPPER.getSchema() + " where tc.id=?";
        Collection<TaxComponentData> taxComponents = this.jdbcTemplate.query(sql, TAX_COMPONENT_MAPPER, id); // NOSONAR

        if (taxComponents == null || taxComponents.isEmpty()) {
            return null;
        } else {
            return taxComponents.stream().findFirst().orElse(null); // NOSONAR
        }
    }

    @Override
    public TaxComponentData retrieveTaxComponentTemplate() {
        return TaxComponentData.template(this.accountingDropdownReadPlatformService.retrieveAccountMappingOptions(),
                this.accountingDropdownReadPlatformService.retrieveGLAccountTypeOptions());
    }

    @Override
    public Collection<TaxGroupData> retrieveAllTaxGroups() {
        String sql = "select " + TAX_GROUP_MAPPER.getSchema();
        return this.jdbcTemplate.query(sql, TAX_GROUP_MAPPER); // NOSONAR
    }

    @Override
    public TaxGroupData retrieveTaxGroupData(final Long id) {
        String sql = "select " + TAX_GROUP_MAPPER.getSchema() + " where tg.id=?";

        Collection<TaxGroupData> taxGroups = this.jdbcTemplate.query(sql, TAX_GROUP_MAPPER, id); // NOSONAR
        if (taxGroups == null || taxGroups.isEmpty()) {
            return null;
        } else {
            return taxGroups.stream().findFirst().orElse(null); // NOSONAR
        }
    }

    @Override
    public TaxGroupData retrieveTaxGroupWithTemplate(final Long id) {
        TaxGroupData taxGroupData = retrieveTaxGroupData(id);
        taxGroupData = TaxGroupData.template(taxGroupData, retrieveTaxComponentsForLookUp());
        return taxGroupData;
    }

    @Override
    public TaxGroupData retrieveTaxGroupTemplate() {
        return TaxGroupData.template(retrieveTaxComponentsForLookUp());
    }

    private Collection<TaxComponentData> retrieveTaxComponentsForLookUp() {
        String sql = "select " + TAX_COMPONENT_LOOK_UP_MAPPER.getSchema();
        return this.jdbcTemplate.query(sql, TAX_COMPONENT_LOOK_UP_MAPPER); // NOSONAR
    }

    @Override
    public List<TaxGroupData> retrieveTaxGroupsForLookUp() {
        String sql = "select " + TAX_GROUP_LOOK_UP_MAPPER.getSchema();
        return this.jdbcTemplate.query(sql, TAX_GROUP_LOOK_UP_MAPPER); // NOSONAR
    }

    @Getter
    private static final class TaxComponentExtractor implements ResultSetExtractor<Collection<TaxComponentData>> {

        private final String schema;
        private final TaxComponentHistoryDataMapper componentHistoryDataMapper = new TaxComponentHistoryDataMapper();

        TaxComponentExtractor() {
            StringBuilder sb = new StringBuilder();
            sb.append("tc.id as id, tc.name as name,");
            sb.append("tc.percentage as percentage, tc.start_date as startDate,");
            sb.append("tc.debit_account_type_enum as debitAccountTypeEnum,");
            sb.append("dgl.id as debitAccountId, dgl.name as debitAccountName,  dgl.gl_code as debitAccountGlCode,");
            sb.append("tc.credit_account_type_enum as creditAccountTypeEnum,");
            sb.append("cgl.id as creditAccountId, cgl.name as creditAccountName,  cgl.gl_code as creditAccountGlCode,");
            sb.append("history.percentage as historyPercentage, history.start_date as historyStartDate,");
            sb.append("history.end_date as historyEndDate");
            sb.append(" from m_tax_component tc ");
            sb.append(" left join acc_gl_account dgl on dgl.id = tc.debit_account_id");
            sb.append(" left join acc_gl_account cgl on cgl.id = tc.credit_account_id");
            sb.append(" left join m_tax_component_history history on history.tax_component_id = tc.id");

            this.schema = sb.toString();
        }

        @Override
        public Collection<TaxComponentData> extractData(ResultSet rs) throws SQLException {
            Map<Long, TaxComponentData> componentsMap = new LinkedHashMap<>();

            while (rs.next()) {
                Long id = rs.getLong("id");

                TaxComponentData existing = componentsMap.get(id);
                if (existing == null) {

                    String name = rs.getString("name");
                    BigDecimal percentage = rs.getBigDecimal("percentage");

                    // Debit Account Type
                    Integer debitAccountTypeEnum = JdbcSupport.getIntegerDefaultToNullIfZero(rs, "debitAccountTypeEnum");
                    EnumOptionData debitAccountType = (debitAccountTypeEnum != null)
                            ? AccountingEnumerations.gLAccountType(debitAccountTypeEnum)
                            : null;

                    GLAccountData debitAccountData = null;
                    if (debitAccountTypeEnum != null && debitAccountTypeEnum > 0) {
                        Long debitAccountId = rs.getLong("debitAccountId");
                        String debitAccountName = rs.getString("debitAccountName");
                        String debitAccountGlCode = rs.getString("debitAccountGlCode");
                        debitAccountData = new GLAccountData().setId(debitAccountId).setName(debitAccountName)
                                .setGlCode(debitAccountGlCode);
                    }

                    // Credit Account Type
                    Integer creditAccountTypeEnum = JdbcSupport.getIntegerDefaultToNullIfZero(rs, "creditAccountTypeEnum");
                    EnumOptionData creditAccountType = (creditAccountTypeEnum != null)
                            ? AccountingEnumerations.gLAccountType(creditAccountTypeEnum)
                            : null;

                    GLAccountData creditAccountData = null;
                    if (creditAccountTypeEnum != null && creditAccountTypeEnum > 0) {
                        Long creditAccountId = rs.getLong("creditAccountId");
                        String creditAccountName = rs.getString("creditAccountName");
                        String creditAccountGlCode = rs.getString("creditAccountGlCode");
                        creditAccountData = new GLAccountData().setId(creditAccountId).setName(creditAccountName)
                                .setGlCode(creditAccountGlCode);
                    }

                    LocalDate startDate = JdbcSupport.getLocalDate(rs, "startDate");

                    componentsMap.put(id, TaxComponentData.instance(id, name, percentage, debitAccountType, debitAccountData,
                            creditAccountType, creditAccountData, startDate, new ArrayList<>()));
                }

                // Add history entry
                TaxComponentData component = componentsMap.get(id);
                component.getTaxComponentHistories().add(componentHistoryDataMapper.mapRow(rs, 0));
            }

            return componentsMap.values();
        }
    }

    private static final class TaxComponentHistoryDataMapper implements RowMapper<TaxComponentHistoryData> {

        @Override
        public TaxComponentHistoryData mapRow(ResultSet rs, @SuppressWarnings("unused") int rowNum) throws SQLException {
            final BigDecimal percentage = rs.getBigDecimal("historyPercentage");
            final LocalDate startDate = JdbcSupport.getLocalDate(rs, "historyStartDate");
            final LocalDate endDate = JdbcSupport.getLocalDate(rs, "historyEndDate");
            return new TaxComponentHistoryData(percentage, startDate, endDate);
        }

    }

    private static final class TaxGroupExtractor implements ResultSetExtractor<Collection<TaxGroupData>> {

        @Getter
        private final String schema;
        private final TaxGroupMappingsDataMapper taxGroupMappingsDataMapper = new TaxGroupMappingsDataMapper();

        TaxGroupExtractor() {
            StringBuilder sb = new StringBuilder();
            sb.append("tg.id as id, tg.name as name,");
            sb.append("tgm.id as mappingId,");
            sb.append("tc.id as taxComponentId, tc.name as taxComponentName, tc.percentage as percentage,");
            sb.append("tgm.start_date as startDate, tgm.end_date as endDate ");
            sb.append(" from m_tax_group tg ");
            sb.append(" inner join m_tax_group_mappings tgm on tgm.tax_group_id = tg.id ");
            sb.append(" inner join m_tax_component tc on tc.id = tgm.tax_component_id ");
            this.schema = sb.toString();
        }

        @Override
        public Collection<TaxGroupData> extractData(ResultSet rs) throws SQLException {
            final Map<Long, TaxGroupData> taxGroupMap = new LinkedHashMap<>();

            while (rs.next()) {
                final Long id = rs.getLong("id");
                final String name = rs.getString("name");

                TaxGroupData taxGroup = taxGroupMap.computeIfAbsent(id, i -> TaxGroupData.instance(i, name, new ArrayList<>()));

                taxGroup.getTaxAssociations().add(taxGroupMappingsDataMapper.mapRow(rs, 0));
            }

            return taxGroupMap.values();
        }

    }

    private static final class TaxGroupMappingsDataMapper implements RowMapper<TaxGroupMappingsData> {

        @Override
        public TaxGroupMappingsData mapRow(ResultSet rs, @SuppressWarnings("unused") int rowNum) throws SQLException {
            final Long mappingId = rs.getLong("mappingId");
            final Long id = rs.getLong("taxComponentId");
            final String name = rs.getString("taxComponentName");
            final BigDecimal percentage = rs.getBigDecimal("percentage");
            TaxComponentData componentData = TaxComponentData.lookup(id, name, percentage);

            final LocalDate startDate = JdbcSupport.getLocalDate(rs, "startDate");
            final LocalDate endDate = JdbcSupport.getLocalDate(rs, "endDate");
            return new TaxGroupMappingsData(mappingId, componentData, startDate, endDate);
        }

    }

    @Getter
    private static final class TaxComponentLookUpMapper implements RowMapper<TaxComponentData> {

        private final String schema;

        TaxComponentLookUpMapper() {
            StringBuilder sb = new StringBuilder();
            sb.append("tc.id as id, tc.name as name ");
            sb.append(" from m_tax_component tc ");
            this.schema = sb.toString();
        }

        @Override
        public TaxComponentData mapRow(ResultSet rs, @SuppressWarnings("unused") int rowNum) throws SQLException {
            final Long id = rs.getLong("id");
            final String name = rs.getString("name");
            return TaxComponentData.lookup(id, name, null);
        }

    }

    @Getter
    private static final class TaxGroupLookUpMapper implements RowMapper<TaxGroupData> {

        private final String schema;

        TaxGroupLookUpMapper() {
            StringBuilder sb = new StringBuilder();
            sb.append("tg.id as id, tg.name as name ");
            sb.append(" from m_tax_group tg ");
            this.schema = sb.toString();
        }

        @Override
        public TaxGroupData mapRow(ResultSet rs, @SuppressWarnings("unused") int rowNum) throws SQLException {
            final Long id = rs.getLong("id");
            final String name = rs.getString("name");
            return TaxGroupData.lookup(id, name);
        }

    }

}
