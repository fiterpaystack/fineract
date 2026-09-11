package com.paystack.fineract.infrastructure.dataqueries.service;

import jakarta.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.exception.ErrorHandler;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.dataqueries.service.GenericDataService;
import org.apache.fineract.infrastructure.dataqueries.service.ReadReportingServiceImpl;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.security.service.SqlInjectionPreventerService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Primary
@Service
@Slf4j
public class PaystackReadReportingServiceImpl extends ReadReportingServiceImpl implements PaystackReadReportingService {

    private static final int REPORT_EXCEL_FETCH_SIZE = 1000;
    private static final int SXSSF_ROW_WINDOW_SIZE = 100;
    private static final int MAX_EXCEL_COLUMN_WIDTH = 80 * 256;

    /**
     * Columns that should always be treated as strings even if they contain numeric values. These values are fetched
     * from the string_columns table in the database. The Excel export will check this list and force string data type
     * for these columns.
     */
    private final Set<String> stringColumns = new HashSet<>();

    /**
     * Timestamp when the string columns were last loaded from the database. Used to determine when to refresh the
     * cache.
     */
    private Instant lastLoadTime = Instant.MIN;

    @Autowired
    public PaystackReadReportingServiceImpl(JdbcTemplate jdbcTemplate, PlatformSecurityContext context,
            GenericDataService genericDataService, SqlInjectionPreventerService sqlInjectionPreventerService,
            DatabaseSpecificSQLGenerator sqlGenerator, FineractProperties fineractProperties) {
        super(jdbcTemplate, context, genericDataService, sqlInjectionPreventerService, sqlGenerator, fineractProperties);
    }

    /**
     * Loads string columns from the database if the cache is expired or empty. The cache expires after 1 hour.
     */
    public void loadStringColumns() {
        if (isCacheExpired()) {
            refreshStringColumns();
        }
    }

    /**
     * Checks if the string columns cache has expired. The cache expires after 1 hour.
     *
     * @return true if the cache has expired, false otherwise
     */
    private boolean isCacheExpired() {
        return lastLoadTime.plus(1, ChronoUnit.HOURS).isBefore(Instant.now());
    }

    /**
     * Refreshes the string columns cache by loading the values from the database.
     */
    private void refreshStringColumns() {
        try {
            log.info("Loading string columns from database");
            List<String> columns = jdbcTemplate.query("SELECT column_name FROM string_columns",
                    (rs, rowNum) -> rs.getString("column_name"));

            // Clear and update the cache
            stringColumns.clear();
            stringColumns.addAll(columns);

            // Update the last load time
            lastLoadTime = Instant.now();

            log.info("Loaded {} string columns from database", columns.size());
        } catch (DataAccessException e) {
            log.error("Error loading string columns from database", e);
            // If there's an error, we'll continue with the current set
        }
    }

    @Override
    public StreamingOutput retrieveReportExcel(String name, String type, Map<String, String> extractedQueryParams) {
        return out -> {
            try {
                final String sql = getSQLtoRun(name, type, extractedQueryParams, false);
                streamExcelResultset(sql, out);
            } catch (final Exception e) {
                throw ErrorHandler.getMappable(e);
            }
        };
    }

    private void streamExcelResultset(final String sql, final OutputStream out) throws IOException {
        try {
            this.jdbcTemplate.query(connection -> {
                final PreparedStatement statement = connection.prepareStatement(sql);
                statement.setFetchSize(REPORT_EXCEL_FETCH_SIZE);
                return statement;
            }, resultSet -> {
                try {
                    generateExcelFileBuffer(resultSet, out);
                    return null;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw new IOException(e);
        }
    }

    private void generateExcelFileBuffer(final ResultSet resultSet, OutputStream out) throws SQLException, IOException {
        this.loadStringColumns();

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(SXSSF_ROW_WINDOW_SIZE)) {
            workbook.setCompressTempFiles(true);
            Sheet sheet = workbook.createSheet("Data");

            // Create cell style for headers (bold)
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            // Create cell style for numbers
            CellStyle numberStyle = workbook.createCellStyle();
            DataFormat format = workbook.createDataFormat();
            numberStyle.setDataFormat(format.getFormat("#,##0.00"));

            // Create cell style for dates
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(format.getFormat("dd/MM/yyyy"));

            // Create cell style for timestamps (dates with time)
            CellStyle dateTimeStyle = workbook.createCellStyle();
            dateTimeStyle.setDataFormat(format.getFormat("dd/MM/yyyy HH:mm:ss"));

            // Create headers
            Row headerRow = sheet.createRow(0);
            final ResultSetMetaData metadata = resultSet.getMetaData();
            final int columnCount = metadata.getColumnCount();
            final String[] columnNames = new String[columnCount];
            for (int i = 0; i < columnCount; i++) {
                columnNames[i] = metadata.getColumnLabel(i + 1);
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columnNames[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, Math.min(MAX_EXCEL_COLUMN_WIDTH, Math.max(12, columnNames[i].length() + 2) * 256));
            }

            // Create data rows
            int rowIndex = 1;
            while (resultSet.next()) {
                Row row = sheet.createRow(rowIndex++);
                for (int j = 0; j < columnCount; j++) {
                    Cell cell = row.createCell(j);
                    writeCellValue(cell, resultSet.getObject(j + 1), columnNames[j], numberStyle, dateStyle, dateTimeStyle);
                }
            }

            workbook.write(out);
        }
    }

    private void writeCellValue(final Cell cell, final Object value, final String columnName, final CellStyle numberStyle,
            final CellStyle dateStyle, final CellStyle dateTimeStyle) {
        if (value == null) {
            return;
        }

        if (this.stringColumns.contains(columnName)) {
            cell.setCellValue(value.toString());
        } else if (value instanceof java.sql.Date dateValue) {
            cell.setCellValue(dateValue);
            cell.setCellStyle(dateStyle);
        } else if (value instanceof Timestamp timestampValue) {
            cell.setCellValue(timestampValue);
            cell.setCellStyle(dateTimeStyle);
        } else if (value instanceof LocalDate localDateValue) {
            cell.setCellValue(localDateValue);
            cell.setCellStyle(dateStyle);
        } else if (value instanceof LocalDateTime localDateTimeValue) {
            cell.setCellValue(localDateTimeValue);
            cell.setCellStyle(dateTimeStyle);
        } else if (value instanceof Number numberValue) {
            cell.setCellValue(numberValue instanceof BigDecimal ? ((BigDecimal) numberValue).doubleValue() : numberValue.doubleValue());
            cell.setCellStyle(numberStyle);
        } else {
            writeStringCellValue(cell, value.toString(), columnName, dateStyle, dateTimeStyle, numberStyle);
        }
    }

    private void writeStringCellValue(final Cell cell, final String cellValue, final String columnName, final CellStyle dateStyle,
            final CellStyle dateTimeStyle, final CellStyle numberStyle) {
        if (columnName.contains("Date")) {
            writeDateStringCellValue(cell, cellValue, dateStyle, dateTimeStyle);
            return;
        }

        try {
            cell.setCellValue(Double.parseDouble(cellValue));
            cell.setCellStyle(numberStyle);
        } catch (NumberFormatException e) {
            cell.setCellValue(cellValue);
        }
    }

    private void writeDateStringCellValue(final Cell cell, final String cellValue, final CellStyle dateStyle,
            final CellStyle dateTimeStyle) {
        try {
            final SimpleDateFormat sqlDateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            final Date dateValue = sqlDateTimeFormat.parse(cellValue);
            cell.setCellValue(dateValue);
            cell.setCellStyle("00:00:00".equals(new SimpleDateFormat("HH:mm:ss").format(dateValue)) ? dateStyle : dateTimeStyle);
        } catch (ParseException e1) {
            try {
                final SimpleDateFormat sqlDateFormat = new SimpleDateFormat("yyyy-MM-dd");
                cell.setCellValue(sqlDateFormat.parse(cellValue));
                cell.setCellStyle(dateStyle);
            } catch (ParseException e2) {
                cell.setCellValue(cellValue);
            }
        }
    }

}
