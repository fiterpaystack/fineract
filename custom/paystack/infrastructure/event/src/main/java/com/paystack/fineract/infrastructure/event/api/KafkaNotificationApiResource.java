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
package com.paystack.fineract.infrastructure.event.api;

import com.paystack.fineract.commands.service.PaystackCommandWrapperBuilder;
import com.paystack.fineract.infrastructure.event.external.domain.KafkaNotificationDTO;
import com.paystack.fineract.infrastructure.event.external.domain.KafkaNotificationStatus;
import com.paystack.fineract.infrastructure.event.external.service.KafkaNotificationReadPlatformService;
import com.paystack.fineract.infrastructure.event.external.service.command.KafkaNotificationCommandHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.api.DateParam;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.DateFormat;
import org.apache.fineract.infrastructure.core.serialization.ApiRequestJsonSerializationSettings;
import org.apache.fineract.infrastructure.core.serialization.ToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.stereotype.Component;

/**
 * REST API for Paystack Kafka Notification operations
 */
@Path("/v1/kafka/notifications")
@Component
@Tag(name = "Kafka Notifications", description = "Kafka notification monitoring and logging")
@RequiredArgsConstructor
@Slf4j
public class KafkaNotificationApiResource {

    private static final String RESOURCE_NAME_FOR_PERMISSIONS = "KAFKANOTIFICATION";

    private final PlatformSecurityContext context;
    private final ToApiJsonSerializer<KafkaNotificationDTO> toApiJsonSerializer;
    private final ApiRequestParameterHelper apiRequestParameterHelper;
    private final KafkaNotificationReadPlatformService kafkaNotificationReadPlatformService;
    private final KafkaNotificationCommandHandler kafkaNotificationCommandHandler;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    /**
     * Search and retrieve Kafka notifications with pagination and filtering
     */
    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Search Kafka Notifications", description = "Retrieve paginated list of Kafka notifications with optional filtering by customer, event type, account, status, and date range. Results are ordered by creation date (most recent first). If no filters are applied, returns all notifications.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = KafkaNotificationDTO.class))) })
    public String searchNotifications(@Context final UriInfo uriInfo,
            @QueryParam("customerId") @Parameter(description = "Customer ID filter") final Long customerId,
            @QueryParam("eventType") @Parameter(description = "Event type filter (partial match)") final String eventType,
            @QueryParam("accountId") @Parameter(description = "Account ID filter") final Long accountId,
            @QueryParam("status") @Parameter(description = "Notification status filter") final String status,
            @QueryParam("fromDate") @Parameter(description = "Start date filter (inclusive)") final DateParam fromDateParam,
            @QueryParam("toDate") @Parameter(description = "End date filter (inclusive)") final DateParam toDateParam,
            @QueryParam("limit") @DefaultValue("50") @Parameter(description = "Maximum number of results per page") final Integer limit,
            @QueryParam("offset") @DefaultValue("0") @Parameter(description = "Number of results to skip") final Integer offset,
            @QueryParam("locale") final String locale, @QueryParam("dateFormat") final String rawDateFormat) {

        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME_FOR_PERMISSIONS);

        // Validate pagination parameters using service layer
        kafkaNotificationReadPlatformService.validatePaginationParameters(limit, offset);

        // Convert DateParam to LocalDateTime
        DateFormat dateFormat = new DateFormat(DateUtils.DEFAULT_DATE_FORMAT);
        LocalDateTime fromDate = null;
        if (fromDateParam != null) {
            fromDate = fromDateParam.getDate("fromDate", dateFormat, locale).atStartOfDay();
        }
        LocalDateTime toDate = null;
        if (toDateParam != null) {
            toDate = toDateParam.getDate("toDate", dateFormat, locale).plusDays(1).atStartOfDay();
        }

        // Parse status enum using service layer validation
        KafkaNotificationStatus notificationStatus = kafkaNotificationReadPlatformService.validateAndParseStatus(status);

        // Search notifications
        Page<KafkaNotificationDTO> notifications = kafkaNotificationReadPlatformService.searchNotifications(customerId, eventType,
                accountId, notificationStatus, fromDate, toDate, limit, offset);

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.toApiJsonSerializer.serialize(settings, notifications);
    }

    /**
     * Retrieve a specific Kafka notification by ID
     */
    @GET
    @Path("/{notificationId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve Kafka Notification", description = "Get a specific Kafka notification by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = KafkaNotificationDTO.class))),
            @ApiResponse(responseCode = "404", description = "Notification not found") })
    public String retrieveNotification(@Context final UriInfo uriInfo,
            @PathParam("notificationId") @Parameter(description = "Notification ID") final Long notificationId) {

        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME_FOR_PERMISSIONS);

        // Validate notification exists using service layer
        kafkaNotificationReadPlatformService.validateNotificationExists(notificationId);

        final KafkaNotificationDTO notification = kafkaNotificationReadPlatformService.getNotificationById(notificationId);

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.toApiJsonSerializer.serialize(settings, notification);
    }

    /**
     * Retrieve notifications for a specific account
     */
    @GET
    @Path("/accounts/{accountId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve Notifications by Account", description = "Get paginated list of Kafka notifications for a specific account, ordered by creation date (most recent first)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = KafkaNotificationDTO.class))) })
    public String retrieveNotificationsByAccount(@Context final UriInfo uriInfo,
            @PathParam("accountId") @Parameter(description = "Account ID") final Long accountId,
            @QueryParam("limit") @DefaultValue("50") @Parameter(description = "Maximum number of results per page") final Integer limit,
            @QueryParam("offset") @DefaultValue("0") @Parameter(description = "Number of results to skip") final Integer offset) {

        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME_FOR_PERMISSIONS);

        // Validate pagination parameters using service layer
        kafkaNotificationReadPlatformService.validatePaginationParameters(limit, offset);

        // Retrieve notifications by account
        Page<KafkaNotificationDTO> notifications = kafkaNotificationReadPlatformService.getNotificationsByAccount(accountId, limit, offset);

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.toApiJsonSerializer.serialize(settings, notifications);
    }

    /**
     * Retrieve notifications by status
     */
    @GET
    @Path("/status/{status}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve Notifications by Status", description = "Get paginated list of Kafka notifications by status, ordered by creation date (most recent first)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = KafkaNotificationDTO.class))) })
    public String retrieveNotificationsByStatus(@Context final UriInfo uriInfo,
            @PathParam("status") @Parameter(description = "Notification status") final String status,
            @QueryParam("limit") @DefaultValue("50") @Parameter(description = "Maximum number of results per page") final Integer limit,
            @QueryParam("offset") @DefaultValue("0") @Parameter(description = "Number of results to skip") final Integer offset) {

        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME_FOR_PERMISSIONS);

        // Validate pagination parameters using service layer
        kafkaNotificationReadPlatformService.validatePaginationParameters(limit, offset);

        // Parse status enum using service layer validation
        KafkaNotificationStatus notificationStatus = kafkaNotificationReadPlatformService.validateAndParseStatus(status);

        // Retrieve notifications by status
        Page<KafkaNotificationDTO> notifications = kafkaNotificationReadPlatformService.getNotificationsByStatus(notificationStatus, limit,
                offset);

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.toApiJsonSerializer.serialize(settings, notifications);
    }

    /**
     * Retry a specific failed notification by ID
     */
    @POST
    @Path("/{notificationId}/retry")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retry Failed Notification", description = "Retry a specific failed Kafka notification by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class))),
            @ApiResponse(responseCode = "404", description = "Notification not found"),
            @ApiResponse(responseCode = "400", description = "Notification is not in FAILED status") })
    public String retryFailedNotification(
            @PathParam("notificationId") @Parameter(description = "Notification ID") final Long notificationId,
            @Parameter(hidden = true) final String apiRequestBodyAsJson) {

        this.context.authenticatedUser().validateHasPermissionTo(RESOURCE_NAME_FOR_PERMISSIONS, List.of("RETRY"));

        final CommandWrapper commandRequest = new PaystackCommandWrapperBuilder().retryKafkaNotification(notificationId)
                .withJson(apiRequestBodyAsJson).build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    /**
     * Retry all failed notifications for a specific account
     */
    @POST
    @Path("/accounts/{accountId}/retry")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retry Failed Notifications by Account", description = "Retry all failed Kafka notifications for a specific account")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class))),
            @ApiResponse(responseCode = "404", description = "Account not found") })
    public String retryFailedNotificationsByAccount(@PathParam("accountId") @Parameter(description = "Account ID") final Long accountId,
            @Parameter(hidden = true) final String apiRequestBodyAsJson) {

        this.context.authenticatedUser().validateHasPermissionTo(RESOURCE_NAME_FOR_PERMISSIONS, List.of("RETRY_BY_ACCOUNT"));

        final CommandWrapper commandRequest = new PaystackCommandWrapperBuilder().retryKafkaNotificationsByAccount(accountId)
                .withJson(apiRequestBodyAsJson).build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    /**
     * Retry all failed notifications in the system
     */
    @POST
    @Path("/retry-all")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retry All Failed Notifications", description = "Retry all failed Kafka notifications in the system")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class))) })
    public String retryAllFailedNotifications(@Parameter(hidden = true) final String apiRequestBodyAsJson) {

        this.context.authenticatedUser().validateHasPermissionTo(RESOURCE_NAME_FOR_PERMISSIONS, List.of("RETRY_ALL"));

        final CommandWrapper commandRequest = new PaystackCommandWrapperBuilder().retryAllKafkaNotifications()
                .withJson(apiRequestBodyAsJson).build();

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

}
