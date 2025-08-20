package com.paystack.fineract.client.charge;

import com.paystack.fineract.client.charge.dto.ChargeSearchResult;
import com.paystack.fineract.client.charge.dto.ClientChargeOverrideRequest;
import com.paystack.fineract.client.charge.dto.ClientChargeResult;
import com.paystack.fineract.client.charge.service.ExtendedClientChargeReadPlatformService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.stereotype.Component;

@Path("/v1/clients/{clientId}/charges/extended")
@Component
@Tag(name = "Client Charges", description = "Client Charge Overrides merged with base charge definitions")
@RequiredArgsConstructor
public class ClientChargeApiResource {

    private final PlatformSecurityContext context;

    private final ExtendedClientChargeReadPlatformService readService;

    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    private final DefaultToApiJsonSerializer<ClientChargeOverrideRequest> toApiJsonSerializer;

    @GET
    @Operation(summary = "List client charges", description = "Paginated list of client-specific charge overrides merged with base charges")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = Page.class))) })
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public Response list(@PathParam("clientId") Long clientId, @QueryParam("limit") Integer limit, @QueryParam("offset") Integer offset) {
        context.authenticatedUser();
        Page<ClientChargeResult> result = readService.listByClient(clientId, limit, offset);
        return Response.ok(result).build();
    }

    @GET
    @Path("/template")
    @Operation(summary = "Retrieve template charges", description = "Returns up to 10 latest charges from m_charge")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ChargeSearchResult.class)))) })
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public Response template(@QueryParam("name") String searchString) {
        context.authenticatedUser();
        if (searchString == null || searchString.isBlank()) {
            searchString = "";
        }
        final List<ChargeSearchResult> charges = readService.retrieveTemplateCharges(searchString.toLowerCase(Locale.ROOT));
        return Response.ok(charges).build();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get a client charge override by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ClientChargeResult.class))),
            @ApiResponse(responseCode = "404", description = "Not Found") })
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public Response get(@PathParam("id") @Parameter(description = "Override id") Long id) {
        context.authenticatedUser();
        ClientChargeResult found = readService.get(id);
        if (found == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(found).build();
    }

    @POST
    @Operation(summary = "Create a new client charge override")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class))) })
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public CommandProcessingResult create(@PathParam("clientId") Long clientId,
            @Parameter(hidden = true) ClientChargeOverrideRequest body) {

        String json = toApiJsonSerializer.serialize(body);
        CommandWrapper wrapper = new CommandWrapper(null, null, clientId, null, null, "CREATE", "CLIENTCHARGEOVERRIDE", null, null,
                "/v1/clients/" + clientId + "/charges/extended", json, null, null, null, null, null, null, null, null, null);
        return commandsSourceWritePlatformService.logCommandSource(wrapper);
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Update an existing client charge override")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class))) })
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public CommandProcessingResult update(@PathParam("clientId") Long clientId, @PathParam("id") Long id,
            @Parameter(hidden = true) ClientChargeOverrideRequest body) {

        String json = toApiJsonSerializer.serialize(body);
        CommandWrapper wrapper = new CommandWrapper(null, null, clientId, null, null, "UPDATE", "CLIENTCHARGEOVERRIDE", id, null,
                "/v1/clients/" + clientId + "/charges/extended/" + id, json, null, null, null, null, null, null, null, null, null);
        return commandsSourceWritePlatformService.logCommandSource(wrapper);
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete a client charge override")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class))) })
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public CommandProcessingResult delete(@PathParam("clientId") Long clientId, @PathParam("id") Long id) {

        CommandWrapper wrapper = new CommandWrapper(null, null, clientId, null, null, "DELETE", "CLIENTCHARGEOVERRIDE", id, null,
                "/v1/clients/" + clientId + "/charges/extended/" + id, null, null, null, null, null, null, null, null, null, null);
        return commandsSourceWritePlatformService.logCommandSource(wrapper);
    }
}
