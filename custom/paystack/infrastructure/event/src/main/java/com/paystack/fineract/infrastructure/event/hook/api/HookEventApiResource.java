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
package com.paystack.fineract.infrastructure.event.hook.api;

import com.paystack.fineract.infrastructure.event.hook.data.HookEventData;
import com.paystack.fineract.infrastructure.event.hook.data.RetryAttemptData;
import com.paystack.fineract.infrastructure.event.hook.domain.HookEventStatus;
import com.paystack.fineract.infrastructure.event.hook.service.HookEventReadPlatformService;
import com.paystack.fineract.infrastructure.event.hook.service.HookEventWritePlatformService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.exception.UnrecognizedQueryParamException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.stereotype.Component;

/**
 * REST API for hook event retry operations.
 */
@Path("/v1/custom/hook-events")
@Component
@Tag(name = "Hook Events", description = "Hook event retry and management APIs")
@RequiredArgsConstructor
@Slf4j
public class HookEventApiResource {

    private static final String RESOURCE_NAME_FOR_PERMISSIONS = "HOOK_EVENT";

    private final PlatformSecurityContext context;
    private final HookEventReadPlatformService readPlatformService;
    private final HookEventWritePlatformService writePlatformService;

    /**
     * Handle commands on hook events (retry single event or retry all failed).
     *
     * Commands: - retry: Retry a single failed event - retryAllFailed: Retry all failed events
     */
    @POST
    @Path("/{eventId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retry Hook Event", description = "Manually retry a failed hook event by event ID. Use command=retry")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Event retry initiated"),
            @ApiResponse(responseCode = "404", description = "Event not found"),
            @ApiResponse(responseCode = "400", description = "Event not in FAILED status or invalid command") })
    public Response handleCommand(@PathParam("eventId") @Parameter(description = "Event ID") final String eventId,
            @QueryParam("command") @Parameter(description = "command") final String commandParam,
            @Parameter(hidden = true) final String apiRequestBodyAsJson) {
        this.context.authenticatedUser().validateHasPermissionTo(RESOURCE_NAME_FOR_PERMISSIONS, List.of("RETRY"));

        if (!"retry".equalsIgnoreCase(commandParam)) {
            throw new UnrecognizedQueryParamException("command", commandParam, new Object[] { "retry" });
        }

        // Delegate to service layer
        writePlatformService.retryEvent(eventId);

        Map<String, Object> response = new HashMap<>();
        response.put("eventId", eventId);
        response.put("status", "retry_initiated");
        response.put("message", "Event retry has been initiated");

        return Response.ok(response).build();
    }

    /**
     * Retry all failed events.
     */
    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retry All Failed Events", description = "Manually retry all failed hook events. Use command=retryAllFailed")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Retry operation completed") })
    public Response retryAllFailed(@QueryParam("command") @Parameter(description = "command") final String commandParam,
            @Parameter(hidden = true) final String apiRequestBodyAsJson) {
        this.context.authenticatedUser().validateHasPermissionTo(RESOURCE_NAME_FOR_PERMISSIONS, List.of("RETRY"));

        if (!"retryAllFailed".equalsIgnoreCase(commandParam)) {
            throw new UnrecognizedQueryParamException("command", commandParam, new Object[] { "retryAllFailed" });
        }

        // Delegate to service layer
        HookEventWritePlatformService.RetryResult result = writePlatformService.retryAllFailed();

        Map<String, Object> response = new HashMap<>();
        if (result.totalEvents() == 0) {
            response.put("message", "No failed events found");
        } else {
            response.put("message", "Retry operation completed");
        }
        response.put("totalEvents", result.totalEvents());
        response.put("successCount", result.successCount());
        response.put("failureCount", result.failureCount());

        return Response.ok(response).build();
    }

    /**
     * Get hook events with optional status filter.
     *
     * @param statusParam
     *            Optional status filter (PENDING, SENT, FAILED, DLQ). If not provided, returns all events.
     */
    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List Hook Events", description = "Retrieve hook events with optional status filter. Use status query parameter to filter by PENDING, SENT, FAILED, or DLQ")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "List of hook events") })
    public Response getHookEvents(
            @QueryParam("status") @Parameter(description = "Filter by status (PENDING, SENT, FAILED, DLQ)") final String statusParam) {
        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME_FOR_PERMISSIONS);

        // Validate and parse status (throws UnrecognizedQueryParamException if invalid)
        HookEventStatus status = readPlatformService.validateAndParseStatus(statusParam);

        // Delegate to service layer
        List<HookEventData> events = readPlatformService.retrieveAll(status);

        Map<String, Object> response = new HashMap<>();
        response.put("total", events.size());
        response.put("status", statusParam != null ? statusParam.toUpperCase() : "ALL");
        response.put("events", events);

        return Response.ok(response).build();
    }

    /**
     * Get retry attempts for a specific event.
     */
    @GET
    @Path("/{eventId}/retry-attempts")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Get Retry Attempts", description = "Retrieve all retry attempts for a specific hook event")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "List of retry attempts"),
            @ApiResponse(responseCode = "404", description = "Event not found") })
    public Response getRetryAttempts(@PathParam("eventId") @Parameter(description = "Event ID") final String eventId) {
        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME_FOR_PERMISSIONS);

        // Delegate to service layer
        List<RetryAttemptData> attempts = readPlatformService.retrieveRetryAttempts(eventId);

        Map<String, Object> response = new HashMap<>();
        response.put("eventId", eventId);
        response.put("totalAttempts", attempts.size());
        response.put("attempts", attempts);

        return Response.ok(response).build();
    }

}
