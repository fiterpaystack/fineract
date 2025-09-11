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

package com.paystack.fineract.discount.api;

import com.paystack.fineract.discount.service.DiscountAnalyticsService;
import com.paystack.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import com.paystack.fineract.infrastructure.security.service.PlatformSecurityContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Discount Analytics API Resource
 * REST endpoints for discount analytics and reporting
 */
@Path("/v1/products/{productId}/discount-analytics")
@Component
@RequiredArgsConstructor
@Tag(name = "Discount Analytics", description = "Analytics and reporting for discount applications")
public class DiscountAnalyticsApiResource {
    
    private final PlatformSecurityContext context;
    private final DiscountAnalyticsService analyticsService;
    private final DefaultToApiJsonSerializer<DiscountAnalyticsService.DiscountAnalyticsReport> serializer;
    
    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Get Discount Analytics", 
               description = "Returns analytics report for discount applications")
    public String getDiscountAnalytics(
            @PathParam("productId") @Parameter(description = "productId") final Long productId,
            @QueryParam("fromDate") @Parameter(description = "fromDate") final String fromDate,
            @QueryParam("toDate") @Parameter(description = "toDate") final String toDate) {
        
        context.authenticatedUser();
        
        LocalDate from = LocalDate.parse(fromDate, DateTimeFormatter.ISO_LOCAL_DATE);
        LocalDate to = LocalDate.parse(toDate, DateTimeFormatter.ISO_LOCAL_DATE);
        
        DiscountAnalyticsService.DiscountAnalyticsReport report = 
            analyticsService.generateAnalyticsReport(from, to, productId);
        
        return serializer.serialize(report);
    }
    
    @GET
    @Path("applications")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Get Discount Applications", 
               description = "Returns list of discount applications for a product")
    public String getDiscountApplications(
            @PathParam("productId") @Parameter(description = "productId") final Long productId) {
        
        context.authenticatedUser();
        
        var applications = analyticsService.getDiscountApplications(productId);
        return serializer.serialize(applications);
    }
    
    @GET
    @Path("total-discount")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Get Total Discount Amount", 
               description = "Returns total discount amount for a product")
    public String getTotalDiscountAmount(
            @PathParam("productId") @Parameter(description = "productId") final Long productId) {
        
        context.authenticatedUser();
        
        var totalAmount = analyticsService.getTotalDiscountAmount(productId);
        return serializer.serialize(totalAmount);
    }
}
