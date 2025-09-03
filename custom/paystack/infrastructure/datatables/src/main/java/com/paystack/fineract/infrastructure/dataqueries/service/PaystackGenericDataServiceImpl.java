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

import static org.apache.fineract.infrastructure.core.service.database.JdbcJavaType.DATE;
import static org.apache.fineract.infrastructure.core.service.database.JdbcJavaType.DATETIME;
import static org.apache.fineract.infrastructure.core.service.database.JdbcJavaType.TIMESTAMP;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import org.apache.fineract.infrastructure.codes.data.CodeValueData;
import org.apache.fineract.infrastructure.codes.service.CodeValueReadPlatformService;
import org.apache.fineract.infrastructure.core.service.database.DatabaseIndependentQueryService;
import org.apache.fineract.infrastructure.core.service.database.DatabaseTypeResolver;
import org.apache.fineract.infrastructure.core.service.database.JdbcJavaType;
import org.apache.fineract.infrastructure.core.service.database.RoutingDataSource;
import org.apache.fineract.infrastructure.dataqueries.data.ResultsetColumnHeaderData;
import org.apache.fineract.infrastructure.dataqueries.data.ResultsetRowData;
import org.apache.fineract.infrastructure.dataqueries.service.DatatableKeywordGenerator;
import org.apache.fineract.infrastructure.dataqueries.service.GenericDataServiceImpl;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.jdbc.support.rowset.SqlRowSetMetaData;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

@Service
@Primary
public class PaystackGenericDataServiceImpl extends GenericDataServiceImpl {

    private final CodeValueReadPlatformService codeValueReadPlatformService;
    private final JdbcTemplate jdbcTemplate;
    public PaystackGenericDataServiceImpl(JdbcTemplate jdbcTemplate, RoutingDataSource dataSource,
            DatabaseIndependentQueryService databaseIndependentQueryService,
            DatatableKeywordGenerator datatableKeywordGenerator, DatabaseTypeResolver databaseTypeResolver,
            CodeValueReadPlatformService codeValueReadPlatformService) {
        super(jdbcTemplate, dataSource, databaseIndependentQueryService, datatableKeywordGenerator, databaseTypeResolver);

        this.codeValueReadPlatformService = codeValueReadPlatformService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @NonNull
    @Override
    public List<ResultsetRowData> fillResultsetRowData(final String sql, List<ResultsetColumnHeaderData> columnHeaders) {
        final SqlRowSet rs = jdbcTemplate.queryForRowSet(sql); // NOSONAR
        return fillResultsetRowData(rs, columnHeaders);
    }

    @NonNull
    private List<ResultsetRowData> fillResultsetRowData(SqlRowSet rs, List<ResultsetColumnHeaderData> columnHeaders) {
        final SqlRowSetMetaData rsmd = rs.getMetaData();
        final List<ResultsetRowData> resultsetDataRows = new ArrayList<>();
        while (rs.next()) {
            final List<Object> columnValues = new ArrayList<>();
            for (int i = 0; i < rsmd.getColumnCount(); i++) {
                final String columnName = rsmd.getColumnName(i + 1);
                final JdbcJavaType colType = columnHeaders.get(i).getColumnType();
                final ResultsetColumnHeaderData.DisplayType columnDisplayType = columnHeaders.get(i).getColumnDisplayType();
                if (colType == DATE) {
                    Date tmpDate = (Date) rs.getObject(columnName);
                    columnValues.add(tmpDate == null ? null : tmpDate.toLocalDate());
                } else if (colType == DATETIME || colType == TIMESTAMP) {
                    Object tmpDate = rs.getObject(columnName);
                    columnValues.add(
                            tmpDate == null ? null : (tmpDate instanceof Timestamp ? ((Timestamp) tmpDate).toLocalDateTime() : tmpDate));
                } else if (columnDisplayType.equals(ResultsetColumnHeaderData.DisplayType.CODELOOKUP)) {
                    CodeValueData data = codeValueReadPlatformService.retrieveCodeValue(rs.getLong(columnName));
                    columnValues.add(data.getName());
                } else {
                    columnValues.add(rs.getObject(columnName));
                }
            }

            resultsetDataRows.add(ResultsetRowData.create(columnValues));
        }
        return resultsetDataRows;
    }
}
