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
package com.paystack.fineract.infrastructure.dataqueries.service;

import io.micrometer.common.util.StringUtils;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.core.service.database.DatabaseType;
import org.apache.fineract.infrastructure.core.service.database.DatabaseTypeResolver;
import org.apache.fineract.infrastructure.dataqueries.data.EntityTables;
import org.apache.fineract.infrastructure.dataqueries.data.GenericResultsetData;
import org.apache.fineract.infrastructure.dataqueries.data.ResultsetColumnHeaderData;
import org.apache.fineract.infrastructure.dataqueries.data.ResultsetRowData;
import org.apache.fineract.infrastructure.dataqueries.service.DatatableUtil;
import org.apache.fineract.infrastructure.dataqueries.service.GenericDataService;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.security.service.SqlValidator;
import org.apache.fineract.infrastructure.security.utils.ColumnValidator;
import org.apache.fineract.portfolio.search.service.SearchUtil;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

import static org.apache.fineract.infrastructure.dataqueries.api.DataTableApiConstant.TABLE_FIELD_ID;

@Service
@Primary
public class PayStackDatatableUtil extends DatatableUtil {
    private final GenericDataService genericDataService;
    private final SqlValidator sqlValidator;
    private final DatabaseSpecificSQLGenerator sqlGenerator;
    private final ColumnValidator columnValidator;
    private final DatabaseTypeResolver databaseTypeResolver;

    public PayStackDatatableUtil(SearchUtil searchUtil, JdbcTemplate jdbcTemplate, SqlValidator sqlValidator, PlatformSecurityContext context, GenericDataService genericDataService, DatabaseSpecificSQLGenerator sqlGenerator, ColumnValidator columnValidator, DatabaseTypeResolver databaseTypeResolver) {
        super(searchUtil, jdbcTemplate, sqlValidator, context, genericDataService, sqlGenerator, columnValidator);

        this.genericDataService = genericDataService;
        this.sqlValidator = sqlValidator;
        this.sqlGenerator = sqlGenerator;
        this.columnValidator = columnValidator;
        this.databaseTypeResolver = databaseTypeResolver;
    }

    @Override
    public GenericResultsetData retrieveDataTableGenericResultSet(final EntityTables entityTable, final String dataTableName,
                                                                  final Long appTableId, final String order, final Long id) {
        final List<ResultsetColumnHeaderData> columnHeaders = genericDataService.fillResultsetColumnHeaders(dataTableName);
        final boolean multiRow = isMultirowDatatable(columnHeaders);

        String fromClause = " from " + sqlGenerator.escape(dataTableName) + " dt ";
        String whereClause = " where " + getFKField(entityTable) + " = " + appTableId;
        sqlValidator.validate(whereClause);
        String sql = "select dt.* ";
        StringBuilder joinClause = new StringBuilder();
        StringBuilder newColumnNames = new StringBuilder();
        StringBuilder selectColumns = new StringBuilder();
        int index = 0;
        for (ResultsetColumnHeaderData columnHeader : columnHeaders) {
            if (columnHeader.getColumnDisplayType().equals(ResultsetColumnHeaderData.DisplayType.CODELOOKUP)) {
                newColumnNames.append(",").append(columnHeader.getColumnCode()).append("Id");
                selectColumns.append(", mcv").append(index).append(".id as ").append(columnHeader.getColumnCode()).append("Id");
                joinClause.append(" join m_code_value mcv").append(index).append(" on dt.")
                        .append(sqlGenerator.escape(columnHeader.getColumnName())).append(" = mcv").append(index).append(".id");
            }
            index++;
        }
        if (StringUtils.isNotBlank(newColumnNames.toString())) {
            DatabaseType dialect = databaseTypeResolver.databaseType();
            sql = sql + selectColumns;
            for(String key : newColumnNames.toString().split(",")) {
                if (key.isBlank()) {
                    continue;
                }
                ResultsetColumnHeaderData header = ResultsetColumnHeaderData.detailed(key, "BIGINT", 32L, true, false,
                        null, null, false, false, dialect);
                header.setVisible(false);
                columnHeaders.add(header);
            }
        }

        sql = sql + fromClause + joinClause + whereClause;
        // id only used for reading a specific entry that belongs to appTableId (in a one to many datatable)
        if (multiRow && id != null) {
            sql = sql + " and " + TABLE_FIELD_ID + " = " + id;
        }
        if (StringUtils.isNotBlank(order)) {
            columnValidator.validateSqlInjection(sql, order);
            sql = sql + " order by " + order;
        }

        final List<ResultsetRowData> result = genericDataService.fillResultsetRowData(sql, columnHeaders);
        return new GenericResultsetData(columnHeaders, result);
    }
}
