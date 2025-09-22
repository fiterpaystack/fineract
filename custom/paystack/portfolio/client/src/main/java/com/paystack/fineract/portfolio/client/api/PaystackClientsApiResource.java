package com.paystack.fineract.portfolio.client.api;

import com.paystack.fineract.commands.service.PaystackCommandWrapperBuilder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.ToApiJsonSerializer;
import org.apache.fineract.portfolio.client.data.ClientData;
import org.springframework.stereotype.Component;

@Path("/v1/clients/{clientId}/extended")
@Component
@Tag(name = "Paystack Client Extended", description = "Extended client operations specific to Paystack integration.\n"
        + "This API provides additional functionality for managing clients beyond the standard Fineract client operations.")
@RequiredArgsConstructor
public class PaystackClientsApiResource {

    private final ToApiJsonSerializer<ClientData> toApiJsonSerializer;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @POST
    @Path("upgrade-to-entity")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Upgrade Client to Entity", description = "Upgrades an existing individual client to an entity (business) client type.\n\n"
            + "This operation allows converting a personal client account to a business entity account, "
            + "which may involve updating client information, legal form, and associated business details.\n\n" + "**Prerequisites:**\n"
            + "- Client must exist and be in an active state\n" + "- Client must not have any active loans or savings accounts\n"
            + "- Required permissions to modify client information\n\n" + "**Business Rules:**\n"
            + "- Only individual clients can be upgraded to entities\n"
            + "- The upgrade process may require additional business information\n"
            + "- Historical transaction data is preserved during the upgrade")
    @RequestBody(required = true, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = PaystackClientsApiResourceSwagger.PostClientsClientIdExtendedUpgradeToEntityRequest.class)))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Client successfully upgraded to entity", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = PaystackClientsApiResourceSwagger.PostClientsClientIdExtendedUpgradeToEntityResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request - Invalid client data or business rules violation", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = PaystackClientsApiResourceSwagger.ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Client not found", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = PaystackClientsApiResourceSwagger.ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Insufficient permissions", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = PaystackClientsApiResourceSwagger.ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = PaystackClientsApiResourceSwagger.ErrorResponse.class))) })
    public String upgradeClientToEntity(
            @PathParam("clientId") @Parameter(description = "The unique identifier of the client to upgrade to entity", required = true, example = "1") final Long clientId,
            @Parameter(hidden = true) final String jsonPayload) {

        final PaystackCommandWrapperBuilder builder = new PaystackCommandWrapperBuilder().withJson(jsonPayload);
        final CommandWrapper commandRequest = builder.upgradeClientToEntity(clientId).build();
        CommandProcessingResult result = commandsSourceWritePlatformService.logCommandSource(commandRequest);
        return toApiJsonSerializer.serialize(result);
    }

}
