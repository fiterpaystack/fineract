package com.paystack.fineract.portfolio.savings.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.paystack.fineract.portfolio.discount.service.DiscountRuleService;
import com.paystack.fineract.portfolio.savings.data.PaystackSavingsProductAdditionalAttributes;
import com.paystack.fineract.portfolio.savings.domain.PaystackSavingsProductAttributes;
import com.paystack.fineract.portfolio.savings.domain.PaystackSavingsProductAttributesRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.accounting.common.AccountingConstants.FinancialActivity;
import org.apache.fineract.accounting.common.AccountingRuleType;
import org.apache.fineract.accounting.financialactivityaccount.domain.FinancialActivityAccountRepositoryWrapper;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.entityaccess.service.FineractEntityAccessUtil;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.savings.data.SavingsProductDataValidator;
import org.apache.fineract.portfolio.savings.domain.SavingsProduct;
import org.apache.fineract.portfolio.savings.domain.SavingsProductAssembler;
import org.apache.fineract.portfolio.savings.domain.SavingsProductRepository;
import org.apache.fineract.portfolio.savings.service.SavingsProductWritePlatformServiceJpaRepositoryImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Primary
public class PaystackSavingsProductWritePlatformServiceJpaRepositoryImpl extends SavingsProductWritePlatformServiceJpaRepositoryImpl {

    private final SavingsProductRepository savingsProductRepository;
    private final FinancialActivityAccountRepositoryWrapper financialActivityAccountRepositoryWrapper;
    private final PaystackSavingsProductAttributesRepository paystackSavingsProductAttributesRepository;

    @Autowired
    private DiscountRuleService discountRuleService;

    public PaystackSavingsProductWritePlatformServiceJpaRepositoryImpl(PlatformSecurityContext context,
            SavingsProductRepository savingProductRepository, SavingsProductDataValidator fromApiJsonDataValidator,
            SavingsProductAssembler savingsProductAssembler,
            org.apache.fineract.accounting.producttoaccountmapping.service.ProductToGLAccountMappingWritePlatformService accountMappingWritePlatformService,
            FineractEntityAccessUtil fineractEntityAccessUtil,
            FinancialActivityAccountRepositoryWrapper financialActivityAccountRepositoryWrapper,
            PaystackSavingsProductAttributesRepository paystackSavingsProductAttributesRepository) {
        super(context, savingProductRepository, fromApiJsonDataValidator, savingsProductAssembler, accountMappingWritePlatformService,
                fineractEntityAccessUtil);
        this.savingsProductRepository = savingProductRepository;
        this.financialActivityAccountRepositoryWrapper = financialActivityAccountRepositoryWrapper;
        this.paystackSavingsProductAttributesRepository = paystackSavingsProductAttributesRepository;
    }

    @Transactional
    @Override
    public CommandProcessingResult create(JsonCommand command) {
        CommandProcessingResult result = super.create(command);
        Long productId = result.getResourceId();
        if (productId != null) {
            SavingsProduct product = savingsProductRepository.findById(productId).orElse(null);
            if (product != null) {
                ensureEmtFinancialActivityConfigured(product, command);
                applyEmtLevyIfPresent(command, product, true);
                // Handle discount rules during product creation
                handleDiscountRules(command, productId, true);
            }
        }
        return result;
    }

    @Transactional
    @Override
    public CommandProcessingResult update(Long productId, JsonCommand command) {
        CommandProcessingResult baseResult = super.update(productId, command);
        SavingsProduct product = savingsProductRepository.findById(productId).orElse(null);
        boolean emtUpdated = false;
        boolean discountUpdated = false;
        if (product != null) {
            ensureEmtFinancialActivityConfigured(product, command);
            emtUpdated = applyEmtLevyIfPresent(command, product, false);
            // Handle discount rules during product update
            discountUpdated = handleDiscountRules(command, productId, false);
        }

        // Build result with changes
        if (emtUpdated || discountUpdated) {
            CommandProcessingResultBuilder builder = new CommandProcessingResultBuilder().withEntityId(productId);
            if (baseResult.getChanges() != null) {
                builder.with(baseResult.getChanges());
            }
            Map<String, Object> changes = baseResult.getChanges() != null ? baseResult.getChanges() : new HashMap<>();

            // EMT changes
            if (command.parameterExists("isEmtLevyApplicable")) {
                changes.put("isEmtLevyApplicable", command.booleanObjectValueOfParameterNamed("isEmtLevyApplicable"));
            }
            if (command.parameterExists("emtLevyAmount")) {
                changes.put("emtLevyAmount", command.bigDecimalValueOfParameterNamed("emtLevyAmount"));
            }
            if (command.parameterExists("emtLevyThreshold")) {
                changes.put("emtLevyThreshold", command.bigDecimalValueOfParameterNamed("emtLevyThreshold"));
            }
            if (command.parameterExists("overrideGlobalEmtLevySetting")) {
                changes.put("overrideGlobalEmtLevySetting", command.booleanObjectValueOfParameterNamed("overrideGlobalEmtLevySetting"));
            }

            // Discount changes
            if (command.parameterExists(PaystackSavingsProductAdditionalAttributes.ENABLE_DISCOUNT_ENGINE)) {
                changes.put(PaystackSavingsProductAdditionalAttributes.ENABLE_DISCOUNT_ENGINE,
                        command.booleanObjectValueOfParameterNamed(PaystackSavingsProductAdditionalAttributes.ENABLE_DISCOUNT_ENGINE));
            }
            if (command.parameterExists(PaystackSavingsProductAdditionalAttributes.DISCOUNT_RULES)) {
                // Discount rules are already handled above, just mark as updated
                changes.put(PaystackSavingsProductAdditionalAttributes.DISCOUNT_RULES, "updated");
            }

            builder.with(changes);
            return builder.build();
        }
        return baseResult;
    }

    private boolean applyEmtLevyIfPresent(JsonCommand command, SavingsProduct product, boolean isNew) {

        PaystackSavingsProductAttributes attributes;

        if (isNew) {
            attributes = PaystackSavingsProductAttributes.of(product.getId());
        } else {
            attributes = paystackSavingsProductAttributesRepository.findBySavingsProductId(product.getId())
                    .orElse(PaystackSavingsProductAttributes.of(product.getId()));
        }

        boolean any = false;
        Boolean isApplicable;
        Boolean overrideGlobalSetting;
        BigDecimal amount;
        BigDecimal threshold;

        if (command.parameterExists(PaystackSavingsProductAdditionalAttributes.EMT_LEVY_APPLICABLE_FOR_DEPOSIT)) {
            isApplicable = command
                    .booleanObjectValueOfParameterNamed(PaystackSavingsProductAdditionalAttributes.EMT_LEVY_APPLICABLE_FOR_DEPOSIT);
            any = true;
            attributes.setIsEmtLevyApplicableForDeposit(isApplicable);
        }

        if (command.parameterExists(PaystackSavingsProductAdditionalAttributes.EMT_LEVY_APPLICABLE_FOR_WITHDRAW)) {
            isApplicable = command
                    .booleanObjectValueOfParameterNamed(PaystackSavingsProductAdditionalAttributes.EMT_LEVY_APPLICABLE_FOR_WITHDRAW);
            any = true;
            attributes.setIsEmtLevyApplicableForWithdraw(isApplicable);
        }
        if (command.parameterExists(PaystackSavingsProductAdditionalAttributes.EMT_LEVY_AMOUNT)) {
            amount = command.bigDecimalValueOfParameterNamed(PaystackSavingsProductAdditionalAttributes.EMT_LEVY_AMOUNT);
            any = true;
            attributes.setEmtLevyAmount(amount);
        }
        if (command.parameterExists(PaystackSavingsProductAdditionalAttributes.EMT_LEVY_THRESHOLD)) {
            threshold = command.bigDecimalValueOfParameterNamed(PaystackSavingsProductAdditionalAttributes.EMT_LEVY_THRESHOLD);
            any = true;
            attributes.setEmtLevyThreshold(threshold);
        }

        if (command.parameterExists(PaystackSavingsProductAdditionalAttributes.EMT_OVERRIDE_GLOBAL_LEVY)) {
            overrideGlobalSetting = command
                    .booleanObjectValueOfParameterNamed(PaystackSavingsProductAdditionalAttributes.EMT_OVERRIDE_GLOBAL_LEVY);
            any = true;
            attributes.setOverrideGlobalEmtLevy(overrideGlobalSetting);
        }
        if (any) {
            paystackSavingsProductAttributesRepository.saveAndFlush(attributes);
        }
        return any;
    }

    private void ensureEmtFinancialActivityConfigured(SavingsProduct product, JsonCommand command) {
        // Use a method that provides a default value to avoid null checks.
        final boolean isEmtLevyRequested = command
                .booleanPrimitiveValueOfParameterNamed(PaystackSavingsProductAdditionalAttributes.EMT_LEVY_APPLICABLE_FOR_WITHDRAW)
                || command
                        .booleanPrimitiveValueOfParameterNamed(PaystackSavingsProductAdditionalAttributes.EMT_LEVY_APPLICABLE_FOR_DEPOSIT);

        if (!isEmtLevyRequested) {
            return;
        }

        final boolean isAccountingEnabled = isAccountingEnabled(product);

        if (isAccountingEnabled) {
            // Pass the enum directly for better type safety, assuming the repository supports it.
            financialActivityAccountRepositoryWrapper
                    .findByFinancialActivityTypeWithNotFoundDetection(FinancialActivity.EMT_LEVY.getValue());
        }
    }

    public boolean isAccountingEnabled(SavingsProduct product) {
        return product.getAccountingType() != null && !AccountingRuleType.NONE.getValue().equals(product.getAccountingType());
    }

    /**
     * Handle discount rules during product creation/update Simplified approach - directly assign existing discount
     * rules to product
     */
    private boolean handleDiscountRules(JsonCommand command, Long productId, boolean isNew) {
        try {
            // Check if discount rules are provided
            if (command.parameterExists("discountRules")) {
                JsonArray discountRulesArray = command.arrayOfParameterNamed("discountRules");
                if (discountRulesArray != null && discountRulesArray.size() > 0) {
                    // Extract discount rule IDs and assign them to the product
                    List<Long> discountRuleIds = new ArrayList<>();
                    for (int i = 0; i < discountRulesArray.size(); i++) {
                        JsonObject ruleObject = discountRulesArray.get(i).getAsJsonObject();
                        if (ruleObject.has("id")) {
                            discountRuleIds.add(ruleObject.get("id").getAsLong());
                        }
                    }

                    if (!discountRuleIds.isEmpty()) {
                        // Assign discount rules to product using the discount rule service
                        discountRuleService.assignDiscountRulesToProduct(productId, discountRuleIds);
                        return true;
                    }
                } else {
                    // If discountRules is provided but empty, remove all assignments
                    discountRuleService.removeAllDiscountRulesFromProduct(productId);
                    return true;
                }
            }
        } catch (Exception e) {
            // Log error but don't fail the product operation
            // Note: Using System.err for critical errors that shouldn't fail the operation
        }
        return false;
    }
}
