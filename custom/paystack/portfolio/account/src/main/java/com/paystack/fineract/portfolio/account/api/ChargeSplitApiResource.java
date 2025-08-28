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

import com.paystack.fineract.portfolio.account.data.ChargeSplitData;
import com.paystack.fineract.portfolio.account.domain.ChargeSplit;
import com.paystack.fineract.portfolio.account.service.ChargeSplitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.CommandWrapperBuilder;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.stereotype.Component;


@Path("/v1/charges/{chargeId}/splits")
@Component
@Tag(name = "Charge Splits", description = "Manage fee splits for charges among stakeholders")
@RequiredArgsConstructor
public class ChargeSplitApiResource {

    private static final String RESOURCE_NAME_FOR_PERMISSIONS = "CHARGE";

    private final PlatformSecurityContext context;
    private final ChargeSplitService splitService;
    private final DefaultToApiJsonSerializer<ChargeSplit> toApiJsonSerializer;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve Stakeholder Splits for Charge", 
               description = "Returns the list of stakeholder splits configured for a charge.")
    public String retrieveSplits(
            @PathParam("chargeId") @Parameter(description = "chargeId") final Long chargeId) {
        context.authenticatedUser();
        List<ChargeSplitData> splits = splitService.getSplitsByChargeId(chargeId);
        return toApiJsonSerializer.serialize(splits);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create Stakeholder Split", 
               description = "Creates a new stakeholder split for a charge")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = ChargeStakeholderSplitRequest.class)))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", 
                        content = @Content(schema = @Schema(implementation = ChargeSplitApiResourceSwagger.PostChargeSplitResponse.class))) })
    public CommandProcessingResult createSplit(
            @PathParam("chargeId") @Parameter(description = "chargeId") final Long chargeId,
            @Parameter(hidden = true) final ChargeStakeholderSplitRequest request) {
        
        final CommandWrapper commandRequest = new CommandWrapperBuilder()
                .createChargeSplit(chargeId)
                .withJson(toApiJsonSerializer.serialize(request))
                .build();
        return commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @PUT
    @Path("{splitId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update Stakeholder Split", 
               description = "Updates an existing stakeholder split")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = ChargeStakeholderSplitRequest.class)))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", 
                        content = @Content(schema = @Schema(implementation = ChargeSplitApiResourceSwagger.PutChargeSplitResponse.class))) })
    public CommandProcessingResult updateSplit(
            @PathParam("chargeId") @Parameter(description = "chargeId") final Long chargeId,
            @PathParam("splitId") @Parameter(description = "splitId") final Long splitId,
            @Parameter(hidden = true) final ChargeStakeholderSplitRequest request) {
        
        final CommandWrapper commandRequest = new CommandWrapperBuilder()
                .updateChargeSplit(chargeId, splitId)
                .withJson(toApiJsonSerializer.serialize(request))
                .build();
        return commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @DELETE
    @Path("{splitId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Delete Stakeholder Split", 
               description = "Deletes a stakeholder split")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", 
                        content = @Content(schema = @Schema(implementation = ChargeSplitApiResourceSwagger.DeleteChargeSplitResponse.class))) })
    public CommandProcessingResult deleteSplit(
            @PathParam("chargeId") @Parameter(description = "chargeId") final Long chargeId,
            @PathParam("splitId") @Parameter(description = "splitId") final Long splitId) {
        
        final CommandWrapper commandRequest = new CommandWrapperBuilder()
                .deleteChargeSplit(chargeId, splitId)
                .build();
        return commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @GET
    @Path("/funds/{fundId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve Splits by Fund", 
               description = "Returns the list of splits for a specific fund")
    public String retrieveSplitsByFund(
            @PathParam("fundId") @Parameter(description = "fundId") final Long fundId) {
        context.authenticatedUser();
        List<ChargeSplitData> splits = splitService.getSplitsByFundId(fundId);
        return toApiJsonSerializer.serialize(splits);
    }
}

// Request DTO
class ChargeStakeholderSplitRequest {
    public Long fundId;
    public String splitType;
    public java.math.BigDecimal splitValue;
    public Long glAccountId;
    public Boolean active;
}

// Swagger documentation classes
class ChargeSplitApiResourceSwagger {
    public static class PostChargeSplitResponse {
        public Long resourceId;
        public Long chargeId;
    }
    
    public static class PutChargeSplitResponse {
        public Long resourceId;
        public Long chargeId;
        public java.util.Map<String, Object> changes;
    }
    
    public static class DeleteChargeSplitResponse {
        public Long resourceId;
        public Long chargeId;
    }
}
