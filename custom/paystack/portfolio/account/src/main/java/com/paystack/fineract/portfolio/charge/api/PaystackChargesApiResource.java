package com.paystack.fineract.portfolio.charge.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.CommandWrapperBuilder;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.ApiRequestJsonSerializationSettings;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.charge.data.ChargeData;
import org.apache.fineract.portfolio.charge.service.ChargeReadPlatformService;
import org.springframework.stereotype.Component;

/**
 * Custom Paystack Charges API Resource that preserves discountRules field during create/update operations. This extends
 * the core Fineract charge functionality with custom discount rule support.
 */
@Path("/v1/paystack/charges")
@Component
@Tag(name = "Paystack Charges", description = "Custom charge management with discount rules support")
@RequiredArgsConstructor
@Slf4j
public class PaystackChargesApiResource {

    private static final String RESOURCE_NAME_FOR_PERMISSIONS = "CHARGE";

    private final PlatformSecurityContext context;
    private final DefaultToApiJsonSerializer<ChargeData> toApiJsonSerializer;
    private final ChargeReadPlatformService readPlatformService;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;
    private final ApiRequestParameterHelper apiRequestParameterHelper;

    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List Charges", description = "Retrieve all charges with discount rules support")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ChargeData.class))) })
    public String retrieveAllCharges(@Context final UriInfo uriInfo) {
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.toApiJsonSerializer.serialize(settings, this.readPlatformService.retrieveAllCharges());
    }

    @GET
    @Path("{chargeId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve a Charge", description = "Returns the details of a defined Charge with discount rules")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ChargeData.class))) })
    public String retrieveCharge(@PathParam("chargeId") @Parameter(description = "chargeId") final Long chargeId,
            @Context final UriInfo uriInfo) {
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        ChargeData charge = this.readPlatformService.retrieveCharge(chargeId);
        if (settings.isTemplate()) {
            final ChargeData templateData = this.readPlatformService.retrieveNewChargeDetails();
            charge = ChargeData.withTemplate(charge, templateData);
        }
        return this.toApiJsonSerializer.serialize(settings, charge);
    }

    @GET
    @Path("template")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve Charge Template", description = "This is a convenience resource for building maintenance user interface screens")
    public String retrieveNewChargeDetails() {
        return this.toApiJsonSerializer.serialize(this.readPlatformService.retrieveNewChargeDetails());
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create/Define a Charge", description = "Define a new charge with discount rules support")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = CommandProcessingResult.class))) })
    public CommandProcessingResult createCharge(@Parameter(hidden = true) final String apiRequestBodyAsJson) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder().createCharge().withJson(apiRequestBodyAsJson).build();

        CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return result;
    }

    @PUT
    @Path("{chargeId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update a Charge", description = "Updates the details of a Charge with discount rules support")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = CommandProcessingResult.class))) })
    public CommandProcessingResult updateCharge(@PathParam("chargeId") @Parameter(description = "chargeId") final Long chargeId,
            @Parameter(hidden = true) final String apiRequestBodyAsJson) {

        final CommandWrapper commandRequest = new CommandWrapperBuilder().updateCharge(chargeId).withJson(apiRequestBodyAsJson).build();

        CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return result;
    }

    @DELETE
    @Path("{chargeId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Delete a Charge", description = "Deletes a Charge")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = CommandProcessingResult.class))) })
    public CommandProcessingResult deleteCharge(@PathParam("chargeId") @Parameter(description = "chargeId") final Long chargeId) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().deleteCharge(chargeId).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }
}
