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
package com.paystack.fineract.portfolio.account.api;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.data.PaginationParameters;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.ApiRequestJsonSerializationSettings;
import org.apache.fineract.infrastructure.core.serialization.ToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import com.paystack.fineract.portfolio.account.domain.FeeSplitAudit;
import com.paystack.fineract.portfolio.account.domain.FeeSplitAuditRepository;
import com.paystack.fineract.portfolio.account.domain.FeeSplitDetail;
import com.paystack.fineract.portfolio.account.domain.FeeSplitDetailRepository;
import com.paystack.fineract.portfolio.account.service.FeeSplitAuditService;
import com.paystack.fineract.portfolio.account.dto.FeeSplitAuditResponse;
import com.paystack.fineract.portfolio.account.dto.FeeSplitDetailResponse;
import com.paystack.fineract.portfolio.account.dto.FeeSplitSummaryResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.apache.fineract.infrastructure.core.api.DateParam;
import org.springframework.format.annotation.DateTimeFormat;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;

/**
 * REST API for Fee Split Audit operations
 */
@Path("/v1/fee-split-audits")
@Component
@Tag(name = "Fee Split Audit", description = "Fee split audit and reporting")
@RequiredArgsConstructor
@Slf4j
public class FeeSplitAuditApiResource {

    private final PlatformSecurityContext context;
    private final ToApiJsonSerializer<Object> toApiJsonSerializer;
    private final ApiRequestParameterHelper apiRequestParameterHelper;
    private final FeeSplitAuditRepository feeSplitAuditRepository;
    private final FeeSplitDetailRepository feeSplitDetailRepository;
    private final FeeSplitAuditService feeSplitAuditService;

    /**
     * Get all fee split audits with pagination
     */
    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve Fee Split Audits", description = "Get paginated list of fee split audit records")
    public String retrieveAllFeeSplitAudits(
            @Context final UriInfo uriInfo,
            @QueryParam("officeId") final Long officeId,
            @QueryParam("fromDate") final DateParam fromDateParam,
            @QueryParam("toDate") final DateParam toDateParam,
            @QueryParam("transactionId") final String transactionId,
            @QueryParam("page") @DefaultValue("0") final int page,
            @QueryParam("size") @DefaultValue("20") final int size,
            @QueryParam("sort") @DefaultValue("splitDate") final String sort,
            @QueryParam("direction") @DefaultValue("desc") final String direction,
            @QueryParam("locale") final String locale,
            @QueryParam("dateFormat") final String rawDateFormat) {

        this.context.authenticatedUser();

        // Validate pagination parameters
        validatePaginationParameters(page, size);

        // Convert DateParam to LocalDate
        LocalDate fromDate = null;
        if (fromDateParam != null) {
            fromDate = fromDateParam.getDate("fromDate", null, locale);
        }
        LocalDate toDate = null;
        if (toDateParam != null) {
            toDate = toDateParam.getDate("toDate", null, locale);
        }

        // Create pageable object
        Sort sortObj = Sort.by(Sort.Direction.fromString(direction), sort);
        Pageable pageable = PageRequest.of(page, size, sortObj);

        // Retrieve audits with filters
        org.springframework.data.domain.Page<FeeSplitAudit> auditPage = 
            feeSplitAuditService.retrieveFeeSplitAudits(officeId, fromDate, toDate, transactionId, pageable);

        // Convert to API response format
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        final Page<FeeSplitAudit> pageResult = new Page<>(auditPage.getContent(), (int) auditPage.getTotalElements());

        return this.toApiJsonSerializer.serialize(settings, pageResult);
    }

    /**
     * Get a specific fee split audit by ID
     */
    @GET
    @Path("/{auditId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve Fee Split Audit", description = "Get a specific fee split audit by ID")
    public String retrieveFeeSplitAudit(@Context final UriInfo uriInfo, @PathParam("auditId") @Parameter(description = "auditId") final Long auditId) {
        this.context.authenticatedUser();

        final FeeSplitAuditResponse audit = this.feeSplitAuditService.getFeeSplitAuditById(auditId);

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.toApiJsonSerializer.serialize(settings, audit);
    }

    /**
     * Get fee split details for a specific audit
     */
    @GET
    @Path("/{auditId}/details")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve Fee Split Audit Details", description = "Get fee split details for a specific audit")
    public String retrieveFeeSplitAuditDetails(@Context final UriInfo uriInfo, @PathParam("auditId") @Parameter(description = "auditId") final Long auditId) {
        this.context.authenticatedUser();

        final List<FeeSplitDetailResponse> details = this.feeSplitAuditService.getFeeSplitDetailsByAuditId(auditId);

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.toApiJsonSerializer.serialize(settings, details);
    }

    /**
     * Get fee split audits for a specific charge
     */
    @GET
    @Path("/charges/{chargeId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve Fee Split Audits by Charge", description = "Get fee split audits for a specific charge")
    public String retrieveFeeSplitAuditsByCharge(@Context final UriInfo uriInfo, @PathParam("chargeId") @Parameter(description = "chargeId") final Long chargeId) {
        this.context.authenticatedUser();

        final List<FeeSplitAuditResponse> audits = this.feeSplitAuditService.retrieveFeeSplitAuditsByCharge(chargeId);

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.toApiJsonSerializer.serialize(settings, audits);
    }

    /**
     * Get fee split summary statistics
     */
    @GET
    @Path("/summary")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve Fee Split Summary", description = "Get fee split summary statistics")
    public String retrieveFeeSplitSummary(
            @Context final UriInfo uriInfo,
            @QueryParam("officeId") final Long officeId,
            @QueryParam("fromDate") final DateParam fromDateParam,
            @QueryParam("toDate") final DateParam toDateParam,
            @QueryParam("locale") final String locale,
            @QueryParam("dateFormat") final String rawDateFormat) {
        
        this.context.authenticatedUser();

        // Convert DateParam to LocalDate
        LocalDate fromDate = null;
        if (fromDateParam != null) {
            fromDate = fromDateParam.getDate("fromDate", null, locale);
        }
        LocalDate toDate = null;
        if (toDateParam != null) {
            toDate = toDateParam.getDate("toDate", null, locale);
        }

        final FeeSplitSummaryResponse summary = this.feeSplitAuditService.retrieveFeeSplitSummary(officeId, fromDate, toDate);

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.toApiJsonSerializer.serialize(settings, summary);
    }

    /**
     * Validate pagination parameters
     */
    private void validatePaginationParameters(final int page, final int size) {
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors)
                .resource("feeSplitAudit");

        if (page < 0) {
            baseDataValidator.reset().parameter("page").value(page)
                    .failWithCodeNoParameterAddedToErrorCode("pagination.page.must.be.positive");
        }

        if (size <= 0 || size > 200) {
            baseDataValidator.reset().parameter("size").value(size)
                    .failWithCodeNoParameterAddedToErrorCode("pagination.size.must.be.between.one.and.two.hundred");
        }

        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }
    }
}
