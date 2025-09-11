package com.paystack.fineract.discount.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystack.fineract.discount.domain.ProductDiscountRule;
import com.paystack.fineract.discount.service.ProductDiscountService;
import com.paystack.fineract.infrastructure.core.data.CommandProcessingResult;
import com.paystack.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import com.paystack.fineract.infrastructure.security.service.PlatformSecurityContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.math.BigDecimal;
import java.util.List;

/**
 * Product Discount API Resource
 * REST endpoints for discount rule management
 */
@Path("/v1/products/{productId}/discount-rules")
@Component
@RestController
@RequiredArgsConstructor
@Tag(name = "Product Discount Rules", description = "Manage discount rules for products")
public class ProductDiscountApiResource {
    
    private final PlatformSecurityContext context;
    private final ProductDiscountService productDiscountService;
    private final DefaultToApiJsonSerializer<ProductDiscountRule> serializer;
    private final ObjectMapper objectMapper;
    
    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve Discount Rules for Product", 
               description = "Returns the list of discount rules configured for a product.")
    public String retrieveDiscountRules(
            @PathParam("productId") @Parameter(description = "productId") final Long productId) {
        
        context.authenticatedUser();
        List<ProductDiscountRule> rules = productDiscountService.getDiscountRules(productId);
        return serializer.serialize(rules);
    }
    
    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create Discount Rule", 
               description = "Creates a new discount rule for a product")
    public CommandProcessingResult createDiscountRule(
            @PathParam("productId") @Parameter(description = "productId") final Long productId,
            @Parameter(hidden = true) final String apiRequestBodyAsJson) {
        
        context.authenticatedUser();
        
        // Parse JSON and create discount rule
        ProductDiscountRule rule = parseDiscountRuleFromJson(apiRequestBodyAsJson);
        rule.setProductId(productId);
        
        ProductDiscountRule createdRule = productDiscountService.createDiscountRule(rule);
        
        return CommandProcessingResult.success(createdRule.getId());
    }
    
    @PUT
    @Path("{ruleId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update Discount Rule", 
               description = "Updates an existing discount rule")
    public CommandProcessingResult updateDiscountRule(
            @PathParam("productId") @Parameter(description = "productId") final Long productId,
            @PathParam("ruleId") @Parameter(description = "ruleId") final Long ruleId,
            @Parameter(hidden = true) final String apiRequestBodyAsJson) {
        
        context.authenticatedUser();
        
        // Parse JSON and update discount rule
        ProductDiscountRule rule = parseDiscountRuleFromJson(apiRequestBodyAsJson);
        rule.setId(ruleId);
        rule.setProductId(productId);
        
        ProductDiscountRule updatedRule = productDiscountService.updateDiscountRule(rule);
        
        return CommandProcessingResult.success(updatedRule.getId());
    }
    
    @DELETE
    @Path("{ruleId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Delete Discount Rule", 
               description = "Deletes a discount rule")
    public CommandProcessingResult deleteDiscountRule(
            @PathParam("productId") @Parameter(description = "productId") final Long productId,
            @PathParam("ruleId") @Parameter(description = "ruleId") final Long ruleId) {
        
        context.authenticatedUser();
        productDiscountService.deleteDiscountRule(ruleId);
        return CommandProcessingResult.success(ruleId);
    }
    
    @POST
    @Path("preview")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Preview Discount Impact", 
               description = "Preview the impact of discount rules on an amount")
    public String previewDiscount(
            @PathParam("productId") @Parameter(description = "productId") final Long productId,
            @Parameter(hidden = true) final String apiRequestBodyAsJson) {
        
        context.authenticatedUser();
        
        // Parse original amount from JSON
        BigDecimal originalAmount = parseOriginalAmountFromJson(apiRequestBodyAsJson);
        
        // Apply discount
        BigDecimal finalAmount = productDiscountService.applyDiscount(productId, originalAmount, null);
        BigDecimal discountAmount = originalAmount.subtract(finalAmount);
        
        // Create response
        String response = String.format(
            "{\"originalAmount\":%s,\"discountAmount\":%s,\"finalAmount\":%s}",
            originalAmount, discountAmount, finalAmount
        );
        
        return response;
    }
    
    /**
     * Parse discount rule from JSON
     */
    private ProductDiscountRule parseDiscountRuleFromJson(String json) {
        try {
            return objectMapper.readValue(json, ProductDiscountRule.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse discount rule JSON: " + json, e);
        }
    }
    
    /**
     * Parse original amount from JSON
     */
    private BigDecimal parseOriginalAmountFromJson(String json) {
        try {
            java.util.Map<String, Object> data = objectMapper.readValue(json, java.util.Map.class);
            Object amount = data.get("originalAmount");
            if (amount instanceof Number) {
                return BigDecimal.valueOf(((Number) amount).doubleValue());
            }
            return BigDecimal.valueOf(100); // Default fallback
        } catch (Exception e) {
            return BigDecimal.valueOf(100); // Default fallback
        }
    }
}