package com.paystack.fineract.portfolio.discount.api;

import com.paystack.fineract.portfolio.discount.data.DiscountRuleData;
import com.paystack.fineract.portfolio.discount.data.DiscountRuleTypeInfo;
import com.paystack.fineract.portfolio.discount.service.DiscountRuleReadPlatformService;
import com.paystack.fineract.portfolio.discount.service.DiscountRuleService;
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
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.domain.CommandWrapper;
import com.paystack.fineract.commands.service.PaystackCommandWrapperBuilder;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.springframework.stereotype.Component;

/**
 * Discount Rule API Resource REST API endpoints for discount rule management
 */
@Path("/v1/discount-rules")
@Component
@Tag(name = "Discount Rules", description = "Manage discount rules for products and charges")
@RequiredArgsConstructor
public class DiscountRuleApiResource {

    private final DiscountRuleReadPlatformService readPlatformService;
    private final DiscountRuleService discountRuleService;
    private final DefaultToApiJsonSerializer<DiscountRuleData> toApiJsonSerializer;
    private final DefaultToApiJsonSerializer<DiscountRuleTypeInfo> ruleTypeToApiJsonSerializer;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List all discount rules", description = "Retrieve all discount rules in the system")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = DiscountRuleData.class))) })
    public List<DiscountRuleData> retrieveAllDiscountRules(@Context UriInfo uriInfo) {
        return readPlatformService.retrieveAllDiscountRules();
    }

    @GET
    @Path("{ruleId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve discount rule", description = "Get a specific discount rule by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = DiscountRuleData.class))) })
    public DiscountRuleData retrieveDiscountRule(@PathParam("ruleId") @Parameter(description = "ruleId") final Long ruleId,
            @Context UriInfo uriInfo) {
        return readPlatformService.retrieveDiscountRule(ruleId);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create discount rule", description = "Create a new discount rule")
    @RequestBody(content = @Content(schema = @Schema(implementation = DiscountRuleData.class)))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class))) })
    public CommandProcessingResult createDiscountRule(@Parameter(hidden = true) DiscountRuleData ruleData) {
        final CommandWrapper commandRequest = new PaystackCommandWrapperBuilder().createDiscountRule()
                .withJson(toApiJsonSerializer.serialize(ruleData)).build();
        return commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @PUT
    @Path("{ruleId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update discount rule", description = "Update an existing discount rule")
    @RequestBody(content = @Content(schema = @Schema(implementation = DiscountRuleData.class)))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class))) })
    public CommandProcessingResult updateDiscountRule(@PathParam("ruleId") @Parameter(description = "ruleId") final Long ruleId,
            @Parameter(hidden = true) DiscountRuleData ruleData) {
        final CommandWrapper commandRequest = new PaystackCommandWrapperBuilder().updateDiscountRule(ruleId)
                .withJson(toApiJsonSerializer.serialize(ruleData)).build();
        return commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @DELETE
    @Path("{ruleId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Delete discount rule", description = "Delete a discount rule (soft delete)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class))) })
    public CommandProcessingResult deleteDiscountRule(@PathParam("ruleId") @Parameter(description = "ruleId") final Long ruleId) {
        final CommandWrapper commandRequest = new PaystackCommandWrapperBuilder().deleteDiscountRule(ruleId).build();
        return commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @GET
    @Path("/types")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Get available discount rule types", description = "Retrieve all available discount rule types and their parameters")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = DiscountRuleTypeInfo.class))) })
    public List<DiscountRuleTypeInfo> getAvailableRuleTypes(@Context UriInfo uriInfo) {
        return discountRuleService.getAvailableRuleTypes();
    }

    @GET
    @Path("/types/{ruleType}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Get rule type information", description = "Get detailed information about a specific rule type")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = DiscountRuleTypeInfo.class))) })
    public DiscountRuleTypeInfo getRuleTypeInfo(@PathParam("ruleType") @Parameter(description = "ruleType") final String ruleType) {
        return discountRuleService.getRuleTypeInfo(ruleType);
    }
}
