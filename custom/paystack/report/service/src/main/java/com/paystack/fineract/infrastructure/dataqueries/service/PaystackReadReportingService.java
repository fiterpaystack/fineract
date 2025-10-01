package com.paystack.fineract.infrastructure.dataqueries.service;

import jakarta.ws.rs.core.StreamingOutput;
import java.util.Map;
import org.apache.fineract.infrastructure.dataqueries.service.ReadReportingService;

public interface PaystackReadReportingService extends ReadReportingService {

    StreamingOutput retrieveReportExcel(String name, String type, Map<String, String> extractedQueryParams);

}
