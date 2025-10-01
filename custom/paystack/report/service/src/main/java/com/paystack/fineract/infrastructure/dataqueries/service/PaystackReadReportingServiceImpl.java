package com.paystack.fineract.infrastructure.dataqueries.service;

import jakarta.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.exception.ErrorHandler;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.dataqueries.data.GenericResultsetData;
import org.apache.fineract.infrastructure.dataqueries.data.ResultsetColumnHeaderData;
import org.apache.fineract.infrastructure.dataqueries.data.ResultsetRowData;
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
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Primary
@Service
@Slf4j
public class PaystackReadReportingServiceImpl extends ReadReportingServiceImpl implements PaystackReadReportingService {

    /**
     * Columns that should always be treated as strings even if they contain numeric values. To add more columns to this
     * list in the future, simply add the column name to this set. The Excel export will check this list and force
     * string data type for these columns.
     */
    private static final Set<String> STRING_COLUMNS = new HashSet<>(
            Arrays.asList("Account No", "Client Account No", "External Id", "CustomerID", "Mobile", "Phone Number", "BVN"));

    @Autowired
    public PaystackReadReportingServiceImpl(JdbcTemplate jdbcTemplate, PlatformSecurityContext context,
            GenericDataService genericDataService, SqlInjectionPreventerService sqlInjectionPreventerService,
            DatabaseSpecificSQLGenerator sqlGenerator, FineractProperties fineractProperties) {
        super(jdbcTemplate, context, genericDataService, sqlInjectionPreventerService, sqlGenerator, fineractProperties);
    }

    @Override
    public StreamingOutput retrieveReportExcel(String name, String type, Map<String, String> extractedQueryParams) {
        return out -> {
            try {
                final GenericResultsetData result = retrieveGenericResultset(name, type, extractedQueryParams, false);
                generateExcelFileBuffer(result, out);
            } catch (final Exception e) {
                throw ErrorHandler.getMappable(e);
            }
        };
    }

    private void generateExcelFileBuffer(final GenericResultsetData result, OutputStream out) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
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
            List<ResultsetColumnHeaderData> columnHeaders = result.getColumnHeaders();
            for (int i = 0; i < columnHeaders.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columnHeaders.get(i).getColumnName());
                cell.setCellStyle(headerStyle);
            }

            // Create data rows
            List<ResultsetRowData> data = result.getData();
            for (int i = 0; i < data.size(); i++) {
                Row row = sheet.createRow(i + 1);
                List<Object> rowData = data.get(i).getRow();

                for (int j = 0; j < rowData.size(); j++) {
                    Cell cell = row.createCell(j);
                    String cellValue = rowData.get(j) != null ? rowData.get(j).toString() : null;

                    if (cellValue != null) {
                        // Get the column name for this cell
                        String columnName = columnHeaders.get(j).getColumnName();

                        // Check if this column should always be treated as a string
                        if (STRING_COLUMNS.contains(columnName)) {
                            // Force as string for specified columns
                            cell.setCellValue(cellValue);
                        } else if (columnName.contains("Date")) {
                            // Format as date for columns with "Date" in the name
                            try {
                                // Try to parse the date - attempt different formats
                                Date dateValue = null;
                                boolean parsedSuccessfully = false;
                                boolean hasTimeComponent = false;

                                // Try SQL datetime format first (yyyy-MM-dd HH:mm:ss)
                                try {
                                    SimpleDateFormat sqlDateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                                    dateValue = sqlDateTimeFormat.parse(cellValue);
                                    parsedSuccessfully = true;

                                    // Check if time component is 00:00:00
                                    SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
                                    String timeComponent = timeFormat.format(dateValue);
                                    hasTimeComponent = !timeComponent.equals("00:00:00");
                                } catch (ParseException e1) {
                                    // Try standard SQL date format (yyyy-MM-dd)
                                    try {
                                        SimpleDateFormat sqlDateFormat = new SimpleDateFormat("yyyy-MM-dd");
                                        dateValue = sqlDateFormat.parse(cellValue);
                                        parsedSuccessfully = true;
                                        hasTimeComponent = false;
                                    } catch (ParseException e2) {
                                        // Both parsing attempts failed
                                        parsedSuccessfully = false;
                                    }
                                }

                                if (parsedSuccessfully) {
                                    // Set as date with the appropriate style based on whether it has a time component
                                    cell.setCellValue(dateValue);
                                    if (hasTimeComponent) {
                                        cell.setCellStyle(dateTimeStyle);
                                    } else {
                                        cell.setCellStyle(dateStyle);
                                    }
                                } else {
                                    // If parsing failed, just display as string
                                    cell.setCellValue(cellValue);
                                }
                            } catch (Exception e) {
                                // If any other error occurs, just display as string
                                cell.setCellValue(cellValue);
                            }
                        } else {
                            // Try to parse as number for proper formatting
                            try {
                                double numValue = Double.parseDouble(cellValue);
                                cell.setCellValue(numValue);
                                cell.setCellStyle(numberStyle);
                            } catch (NumberFormatException e) {
                                // Not a number, set as string
                                cell.setCellValue(cellValue);
                            }
                        }
                    }
                }
            }

            // Auto-size columns
            for (int i = 0; i < columnHeaders.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
        }
    }

}
