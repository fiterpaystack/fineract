package com.paystack.fineract.portfolio.savings.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.paystack.fineract.portfolio.discount.service.DiscountRuleService;
import com.paystack.fineract.portfolio.savings.data.PaystackSavingsProductAdditionalAttributes;
import com.paystack.fineract.portfolio.savings.domain.ExtendedSavingsAccountRepository;
import com.paystack.fineract.portfolio.savings.domain.PaystackSavingsProductAttributes;
import com.paystack.fineract.portfolio.savings.domain.PaystackSavingsProductAttributesRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.accounting.common.AccountingConstants.FinancialActivity;
import org.apache.fineract.accounting.common.AccountingRuleType;
import org.apache.fineract.accounting.financialactivityaccount.domain.FinancialActivityAccountRepositoryWrapper;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.entityaccess.service.FineractEntityAccessUtil;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.apache.fineract.portfolio.savings.data.SavingsProductDataValidator;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountCharge;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsProduct;
import org.apache.fineract.portfolio.savings.domain.SavingsProductAssembler;
import org.apache.fineract.portfolio.savings.domain.SavingsProductRepository;
import org.apache.fineract.portfolio.savings.service.SavingsProductWritePlatformServiceJpaRepositoryImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Primary
@Slf4j
public class PaystackSavingsProductWritePlatformServiceJpaRepositoryImpl extends SavingsProductWritePlatformServiceJpaRepositoryImpl {

    private final SavingsProductRepository savingsProductRepository;
    private final ExtendedSavingsAccountRepository savingsAccountRepository;
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
            PaystackSavingsProductAttributesRepository paystackSavingsProductAttributesRepository,
            SavingsAccountRepository savingsAccountRepository) {
        super(context, savingProductRepository, fromApiJsonDataValidator, savingsProductAssembler, accountMappingWritePlatformService,
                fineractEntityAccessUtil);
        this.savingsProductRepository = savingProductRepository;
        this.savingsAccountRepository = (ExtendedSavingsAccountRepository) savingsAccountRepository;
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
        Map<String, Object> baseChanges = baseResult.getChanges();
        boolean emtUpdated = false;
        boolean discountUpdated = false;
        if (product != null) {
            ensureEmtFinancialActivityConfigured(product, command);
            emtUpdated = applyEmtLevyIfPresent(command, product, false);
            // Handle discount rules during product update
            discountUpdated = handleDiscountRules(command, productId, false);
        }
        this.cascadeChargeChangesToAccounts(product, baseChanges);
        // Build result with changes
        if (emtUpdated || discountUpdated) {
            CommandProcessingResultBuilder builder = new CommandProcessingResultBuilder().withEntityId(productId);
            if (baseChanges != null) {
                builder.with(baseChanges);
            }
            Map<String, Object> changes = baseChanges != null ? baseResult.getChanges() : new HashMap<>();

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
     * Handle discount rules during product creation/update Fixed approach - remove existing assignments first, then
     * assign new ones
     */
    private boolean handleDiscountRules(JsonCommand command, Long productId, boolean isNew) {
        try {
            // Check if discount rules are provided
            if (command.parameterExists("discountRules")) {
                JsonArray discountRulesArray = command.arrayOfParameterNamed("discountRules");

                // Always remove all existing assignments first (for updates)
                if (!isNew) {
                    discountRuleService.removeAllDiscountRulesFromProduct(productId);
                }

                if (discountRulesArray != null && !discountRulesArray.isEmpty()) {
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
                }
                // If discountRules is provided but empty, we already removed all assignments above
                return true;
            }
        } catch (Exception e) {
            // Log error but don't fail the product operation
            log.error("Failed to handle discount rules for product {}", productId, e);
        }
        return false;
    }

    private void cascadeChargeChangesToAccounts(SavingsProduct product, Map<String, Object> changes) {
        if (product == null || changes == null || !changes.containsKey("charges")) {
            return; // do nothing
        }

        log.info("Starting asynchronous cascade of charge changes for product ID: {}", product.getId());
        cascadeChargeChangesToAccountsAsync(product.getId(), product.charges());
    }

    @Async("savingsAsyncExecutor")
    public void cascadeChargeChangesToAccountsAsync(Long productId, Set<Charge> updatedCharges) {
        try {
            long totalAccounts = savingsAccountRepository.countSavingsAccountsByProductId(productId);
            log.info("Starting async charge cascade processing for product ID: {}. Total accounts to process: {}", productId,
                    totalAccounts);
            if (totalAccounts == 0) {
                log.info("No accounts found for product ID: {}. Skipping charge cascade.", productId);
                return;
            }

            int pageSize = 1000; // Process 1000 accounts at a time
            int pageNumber = 0;
            int totalProcessed = 0;
            int totalUpdated = 0;
            int totalErrors = 0;

            Page<SavingsAccount> accountPage;

            do {
                Pageable pageable = PageRequest.of(pageNumber, pageSize);
                accountPage = savingsAccountRepository.findSavingsAccountsByProductId(productId, pageable);

                if (accountPage.hasContent()) {
                    log.info("Processing page {} with {} accounts for product ID: {} (Total: {})", pageNumber,
                            accountPage.getContent().size(), productId, totalAccounts);

                    for (SavingsAccount account : accountPage.getContent()) {
                        try {
                            boolean updated = updateAccountCharges(account, updatedCharges);
                            if (updated) {
                                totalUpdated++;
                            }
                            totalProcessed++;

                            // Log progress every 100 accounts
                            if (totalProcessed % 100 == 0) {
                                log.debug("Progress: {}/{} accounts processed, {} updated, {} errors for product ID: {}", totalProcessed,
                                        totalAccounts, totalUpdated, totalErrors, productId);
                            }
                        } catch (Exception e) {
                            totalErrors++;
                            log.error("Error updating charges for account ID: {} in product ID: {}. Error count: {}", account.getId(),
                                    productId, totalErrors, e);
                            // Continue processing other accounts
                        }
                    }
                }

                pageNumber++;

            } while (accountPage.hasNext());

            log.info(
                    "Completed async charge cascade processing for product ID: {}. "
                            + "Total accounts: {}, Processed: {}, Updated: {}, Errors: {}",
                    productId, totalAccounts, totalProcessed, totalUpdated, totalErrors);

        } catch (Exception e) {
            log.error("Critical error during async charge cascade processing for product ID: {}", productId, e);
        }
    }

    private boolean updateAccountCharges(SavingsAccount account, Set<Charge> updatedCharges) {
        if (updatedCharges == null) {
            return false;
        }

        // Get current charges on the account
        Set<SavingsAccountCharge> currentCharges = account.charges();

        // Create a map of current charges by charge ID for easy lookup
        Map<Long, SavingsAccountCharge> currentChargesMap = new HashMap<>();
        for (SavingsAccountCharge charge : currentCharges) {
            currentChargesMap.put(charge.getCharge().getId(), charge);
        }

        // Create new set of charges for the account
        Set<SavingsAccountCharge> newCharges = new HashSet<>();
        boolean hasChanges = false;

        // Add or update charges based on the product's charges
        for (Charge charge : updatedCharges) {
            SavingsAccountCharge existingAccountCharge = currentChargesMap.get(charge.getId());

            if (existingAccountCharge != null) {
                // Update existing charge with new charge details
                boolean chargeUpdated = updateExistingAccountCharge(existingAccountCharge, charge);
                if (chargeUpdated) {
                    hasChanges = true;
                }
                newCharges.add(existingAccountCharge);
            } else {
                // Create new charge for the account
                SavingsAccountCharge newAccountCharge = createNewAccountCharge(account, charge);
                newCharges.add(newAccountCharge);
                hasChanges = true;
            }
        }

        // Remove charges that are no longer in the product
        for (SavingsAccountCharge currentCharge : currentCharges) {
            boolean stillExists = updatedCharges.stream().anyMatch(charge -> charge.getId().equals(currentCharge.getCharge().getId()));

            if (!stillExists) {
                // Mark charge as inactive instead of removing it to preserve transaction history
                if (currentCharge.isActive()) {
                    currentCharge.inactiavateCharge(DateUtils.getBusinessLocalDate());
                    hasChanges = true;
                }
                newCharges.add(currentCharge);
            }
            // Note: Charges that still exist are already added to newCharges in the first loop above
        }

        // Update the account with the new charges if there were changes
        if (hasChanges) {
            account.update(newCharges);
            this.savingsAccountRepository.saveAndFlush(account);
            return true;
        }

        return false;
    }

    private boolean updateExistingAccountCharge(SavingsAccountCharge accountCharge, Charge updatedCharge) {
        boolean hasChanges = false;

        // Update charge amount if it's a flat charge
        if (updatedCharge.getChargeCalculation() != null && ChargeCalculationType.fromInt(updatedCharge.getChargeCalculation()).isFlat()) {

            BigDecimal currentAmount = accountCharge.getAmount();
            BigDecimal newAmount = updatedCharge.getAmount();

            if (currentAmount == null || !currentAmount.equals(newAmount)) {
                accountCharge.update(newAmount, accountCharge.getDueDate(), updatedCharge.getFeeOnMonthDay(), updatedCharge.feeInterval());
                hasChanges = true;
            }
        }

        return hasChanges;
    }

    private SavingsAccountCharge createNewAccountCharge(SavingsAccount account, Charge charge) {
        // Create a new SavingsAccountCharge based on the Charge definition
        BigDecimal amount = charge.getAmount();
        LocalDate dueDate = null;

        // Set appropriate due date based on charge time type
        if (ChargeTimeType.fromInt(charge.getChargeTimeType()).isOnSpecifiedDueDate()) {
            dueDate = DateUtils.getBusinessLocalDate();
        }

        MonthDay feeOnMonthDay = charge.getFeeOnMonthDay();
        Integer feeInterval = charge.feeInterval();

        SavingsAccountCharge newCharge = SavingsAccountCharge.createNewWithoutSavingsAccount(charge, amount,
                ChargeTimeType.fromInt(charge.getChargeTimeType()), ChargeCalculationType.fromInt(charge.getChargeCalculation()), dueDate,
                true, feeOnMonthDay, feeInterval);

        // Associate the charge with the account
        newCharge.update(account);

        return newCharge;
    }
}
