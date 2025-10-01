package com.paystack.fineract.infrastructure.dataqueries.service.export;

import com.paystack.fineract.infrastructure.dataqueries.service.PaystackReadReportingService;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.dataqueries.service.DatatableExportTargetParameter;
import org.apache.fineract.infrastructure.dataqueries.service.export.DatatableExportUtil;
import org.apache.fineract.infrastructure.dataqueries.service.export.DatatableReportExportService;
import org.apache.fineract.infrastructure.dataqueries.service.export.ResponseHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExcelDatatableReportExportServiceImpl implements DatatableReportExportService {

    private final PaystackReadReportingService readExtraDataAndReportingService;

    @Override
    public ResponseHolder export(String reportName, MultivaluedMap<String, String> queryParams, Map<String, String> reportParams,
            boolean isSelfServiceUserReport, String parameterTypeValue) {
        final StreamingOutput result = this.readExtraDataAndReportingService.retrieveReportExcel(reportName, parameterTypeValue,
                reportParams);
        return new ResponseHolder(Response.Status.OK).contentType("application/vnd.ms-excel")
                .addHeader("Content-Disposition",
                        "attachment;filename=" + DatatableExportUtil.generatePlainExportFileName(255, "xlsx", reportName, reportParams))
                .entity(result);
    }

    @Override
    public boolean supports(DatatableExportTargetParameter exportType) {
        return exportType == DatatableExportTargetParameter.EXCEL;
    }
}
