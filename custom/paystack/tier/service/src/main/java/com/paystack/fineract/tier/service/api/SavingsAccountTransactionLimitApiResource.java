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

package com.paystack.fineract.tier.service.api;

import com.paystack.fineract.tier.service.data.SavingsAccountTransactionLimitsSettingData;
import com.paystack.fineract.tier.service.data.SavingsClientClassificationLimitMappingData;
import com.paystack.fineract.tier.service.service.SavingsAccountGlobalTransactionLimitReadPlatformService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.CommandWrapperBuilder;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.ApiRequestJsonSerializationSettings;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.stereotype.Component;

@Path("/v1/savingsaccount/transactionlimits")
@Component
@Tag(name = "Transaction Limits", description = "Manage base settings for transaction limits in product configurations")
@RequiredArgsConstructor
public class SavingsAccountTransactionLimitApiResource {

    private static final String RESOURCE_NAME_FOR_PERMISSIONS = "TRANSACTIONLIMIT";

    private final PlatformSecurityContext context;
    private final SavingsAccountGlobalTransactionLimitReadPlatformService readPlatformService;
    private final DefaultToApiJsonSerializer<SavingsAccountTransactionLimitsSettingData> limitsToApiJsonSerializer;
    private final DefaultToApiJsonSerializer<SavingsClientClassificationLimitMappingData> classificationToApiJsonSerializer;
    private final ApiRequestParameterHelper apiRequestParameterHelper;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List all transaction limits setting", description = "List all transaction limits in the system")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = SavingsAccountTransactionLimitsApiResourceSwagger.GetSavingsAccountTransactionLimitsSettingResponse.class)))) })
    public String retrieveAllTransactionLimits(@Context final UriInfo uriInfo) {

        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME_FOR_PERMISSIONS);

        final Collection<SavingsAccountTransactionLimitsSettingData> taxComponents = this.readPlatformService.retrieveAll();

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.limitsToApiJsonSerializer.serialize(settings, taxComponents);
    }

    @GET
    @Path("{transactionLimitId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve transaction limit setting", description = "Retrieve a savings account transaction limit setting")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = SavingsAccountTransactionLimitsApiResourceSwagger.GetSavingsAccountTransactionLimitsSettingResponse.class))) })
    public String retrieveATransactionLimits(
            @PathParam("transactionLimitId") @Parameter(description = "transactionLimitId") final Long transactionLimitId,
            @Context final UriInfo uriInfo) {

        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME_FOR_PERMISSIONS);

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());

        SavingsAccountTransactionLimitsSettingData savingsTransactionLimitSetting = this.readPlatformService
                .retrieveOne(transactionLimitId);
        return this.limitsToApiJsonSerializer.serialize(settings, savingsTransactionLimitSetting);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create a new Tax Component", description = "Creates a new Tax Component\n\n"
            + "Mandatory Fields: name, percentage\n\n"
            + "Optional Fields: debitAccountType, debitAcountId, creditAccountType, creditAcountId, startDate")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = SavingsAccountTransactionLimitsApiResourceSwagger.PostSavingsAccountTransactionLimitsSettingRequest.class)))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = SavingsAccountTransactionLimitsApiResourceSwagger.PostSavingsAccountTransactionLimitsSettingResponse.class))) })
    public String createSavingsAccountTransactionLimit(@Parameter(hidden = true) final String apiRequestBodyAsJson) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder().createSavingsTransactionLimitSetting()
                .withJson(apiRequestBodyAsJson).build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.limitsToApiJsonSerializer.serialize(result);
    }

    @PUT
    @Path("{transactionLimitId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update Savings Account Transaction limit Component", description = "Updates transaction limit settings.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = SavingsAccountTransactionLimitsApiResourceSwagger.UpdateSavingsAccountTransactionLimitsSettingRequest.class)))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = SavingsAccountTransactionLimitsApiResourceSwagger.UpdateSavingsAccountTransactionLimitsSettingResponse.class))) })
    public String updateSavingsAccountTransactionLimit(
            @PathParam("transactionLimitId") @Parameter(description = "transactionLimitId") final Long transactionLimitId,
            @Parameter(hidden = true) final String apiRequestBodyAsJson) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder().updateSavingsTransactionLimitSetting(transactionLimitId)
                .withJson(apiRequestBodyAsJson).build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.limitsToApiJsonSerializer.serialize(result);
    }

    @POST
    @Path("classificationmapping")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create a new Tax Component", description = "Creates a new Tax Component\n\n"
            + "Mandatory Fields: name, percentage\n\n"
            + "Optional Fields: debitAccountType, debitAcountId, creditAccountType, creditAcountId, startDate")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = SavingsAccountTransactionLimitsApiResourceSwagger.PostSavingsAccountTransactionLimitsSettingRequest.class)))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = SavingsAccountTransactionLimitsApiResourceSwagger.PostSavingsAccountTransactionLimitsSettingResponse.class))) })
    public String createNewLimitClassificationMapping(@Parameter(hidden = true) final String apiRequestBodyAsJson) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder().createClientClassificationLimitMapping()
                .withJson(apiRequestBodyAsJson).build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.limitsToApiJsonSerializer.serialize(result);
    }

    @PUT
    @Path("classificationmapping/{classificationLimitId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create a new Tax Component", description = "Creates a new Tax Component\n\n"
            + "Mandatory Fields: name, percentage\n\n"
            + "Optional Fields: debitAccountType, debitAcountId, creditAccountType, creditAcountId, startDate")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = SavingsAccountTransactionLimitsApiResourceSwagger.PostSavingsAccountTransactionLimitsSettingRequest.class)))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = SavingsAccountTransactionLimitsApiResourceSwagger.PostSavingsAccountTransactionLimitsSettingResponse.class))) })
    public String updateLimitClassificationMapping(
            @PathParam("classificationLimitId") @Parameter(description = "classificationLimitId") final Long classificationLimitId,
            @Parameter(hidden = true) final String apiRequestBodyAsJson) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder().updateClientClassificationLimitMapping(classificationLimitId)
                .withJson(apiRequestBodyAsJson).build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.limitsToApiJsonSerializer.serialize(result);
    }

    @GET
    @Path("classificationmapping")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List all transaction limits and classification", description = "List all transaction limits in the system")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = SavingsAccountTransactionLimitsApiResourceSwagger.GetSavingsAccountTransactionLimitsSettingResponse.class)))) })
    public String retrieveAllLimitClassificationMappings(@Context final UriInfo uriInfo) {

        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME_FOR_PERMISSIONS);

        final Collection<SavingsClientClassificationLimitMappingData> classificationLimitMappings = this.readPlatformService
                .getLimitClassificationMappings();

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.classificationToApiJsonSerializer.serialize(settings, classificationLimitMappings);
    }

    @GET
    @Path("classificationmapping/{codeValueId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List all transaction limits and classification", description = "List all transaction limits in the system")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = SavingsAccountTransactionLimitsApiResourceSwagger.GetSavingsAccountTransactionLimitsSettingResponse.class)))) })
    public String retrieveClassificationsByClassificationId(
            @PathParam("codeValueId") @Parameter(description = "codeValueId") final Long codeValueId, @Context final UriInfo uriInfo) {

        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME_FOR_PERMISSIONS);

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        if (settings.isTemplate()) {
            final SavingsClientClassificationLimitMappingData classificationLimitMappings = this.readPlatformService
                    .retrieveOneByCodeValueIdWithTemplate(codeValueId);
            return this.classificationToApiJsonSerializer.serialize(settings, classificationLimitMappings);
        } else {
            final SavingsClientClassificationLimitMappingData classificationLimitMappings = this.readPlatformService
                    .retrieveOneByCodeValueId(codeValueId);
            return this.classificationToApiJsonSerializer.serialize(settings, classificationLimitMappings);
        }
    }

    @GET
    @Path("search/{searchKeyWord}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List all transaction limits and classification", description = "List all transaction limits in the system")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = SavingsAccountTransactionLimitsApiResourceSwagger.GetSavingsAccountTransactionLimitsSettingResponse.class)))) })
    public String searchGlobalSettingByName(
            @PathParam("searchKeyWord") @Parameter(description = "searchKeyWord") final String searchKeyWord,
            @Context final UriInfo uriInfo) {

        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME_FOR_PERMISSIONS);

        final Collection<SavingsAccountTransactionLimitsSettingData> globalSettingsSearchResult = this.readPlatformService
                .searchForGlobalSettingByName(searchKeyWord);

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.limitsToApiJsonSerializer.serialize(settings, globalSettingsSearchResult);
    }

}
