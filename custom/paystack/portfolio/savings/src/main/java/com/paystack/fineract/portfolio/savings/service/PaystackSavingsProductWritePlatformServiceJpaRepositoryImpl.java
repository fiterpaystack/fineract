package com.paystack.fineract.portfolio.savings.service;

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

    public PaystackSavingsProductWritePlatformServiceJpaRepositoryImpl(PlatformSecurityContext context,
            SavingsProductRepository savingProductRepository, SavingsProductDataValidator fromApiJsonDataValidator,
            SavingsProductAssembler savingsProductAssembler,
            org.apache.fineract.accounting.producttoaccountmapping.service.ProductToGLAccountMappingWritePlatformService accountMappingWritePlatformService,
            FineractEntityAccessUtil fineractEntityAccessUtil,
            FinancialActivityAccountRepositoryWrapper financialActivityAccountRepositoryWrapper) {
        super(context, savingProductRepository, fromApiJsonDataValidator, savingsProductAssembler, accountMappingWritePlatformService,
                fineractEntityAccessUtil);
        this.savingsProductRepository = savingProductRepository;
        this.financialActivityAccountRepositoryWrapper = financialActivityAccountRepositoryWrapper;
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
                applyEmtLevyIfPresent(command, product);
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
            emtUpdated = applyEmtLevyIfPresent(command, product);
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
                changes.put("overrideGlobalEmtLevySetting", command.bigDecimalValueOfParameterNamed("overrideGlobalEmtLevySetting"));
            }
            builder.with(changes);

            return builder.build();
        }
        return baseResult;
    }

    private boolean applyEmtLevyIfPresent(JsonCommand command, SavingsProduct product) {
        boolean any = false;
        Boolean isApplicable;
        Boolean overrideGlobalSetting;
        BigDecimal amount;
        BigDecimal threshold;

        if (command.parameterExists("isEmtLevyApplicable")) {
            isApplicable = command.booleanObjectValueOfParameterNamed("isEmtLevyApplicable");
            any = true;
            product.setIsEmtLevyApplicable(isApplicable);
        }
        if (command.parameterExists("emtLevyAmount")) {
            amount = command.bigDecimalValueOfParameterNamed("emtLevyAmount");
            any = true;
            product.setEmtLevyAmount(amount);
        }
        if (command.parameterExists("emtLevyThreshold")) {
            threshold = command.bigDecimalValueOfParameterNamed("emtLevyThreshold");
            any = true;
            product.setEmtLevyThreshold(threshold);
        }

        if (command.parameterExists("overrideGlobalEmtLevySetting")) {
            overrideGlobalSetting = command.booleanObjectValueOfParameterNamed("overrideGlobalEmtLevySetting");
            any = true;
            product.setOverrideGlobalEmtLevy(overrideGlobalSetting);
        }
        if (any) {
            savingsProductRepository.saveAndFlush(product);
        }
        return any;
    }

    private void ensureEmtFinancialActivityConfigured(SavingsProduct product, JsonCommand command) {
        // Use a method that provides a default value to avoid null checks.
        final boolean isEmtLevyRequested = command.booleanPrimitiveValueOfParameterNamed("isEmtLevyApplicable");

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
