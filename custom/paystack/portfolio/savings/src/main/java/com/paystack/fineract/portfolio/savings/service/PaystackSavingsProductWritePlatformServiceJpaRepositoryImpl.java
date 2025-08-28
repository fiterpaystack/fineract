package com.paystack.fineract.portfolio.savings.service;

import com.paystack.fineract.portfolio.savings.data.PaystackSavingsProductAdditionalAttributes;
import com.paystack.fineract.portfolio.savings.domain.PaystackSavingsProductAttributes;
import com.paystack.fineract.portfolio.savings.domain.PaystackSavingsProductAttributesRepository;
import java.math.BigDecimal;
import java.util.HashMap;
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
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Primary
public class PaystackSavingsProductWritePlatformServiceJpaRepositoryImpl extends SavingsProductWritePlatformServiceJpaRepositoryImpl {

    private final SavingsProductRepository savingsProductRepository;
    private final FinancialActivityAccountRepositoryWrapper financialActivityAccountRepositoryWrapper;
    private final PaystackSavingsProductAttributesRepository paystackSavingsProductAttributesRepository;

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
        if (product != null) {
            ensureEmtFinancialActivityConfigured(product, command);
            emtUpdated = applyEmtLevyIfPresent(command, product, false);
        }
        if (emtUpdated) {
            CommandProcessingResultBuilder builder = new CommandProcessingResultBuilder().withEntityId(productId);
            if (baseResult.getChanges() != null) {
                builder.with(baseResult.getChanges());
            }
            Map<String, Object> changes = baseResult.getChanges() != null ? baseResult.getChanges() : new HashMap<>();

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
}
