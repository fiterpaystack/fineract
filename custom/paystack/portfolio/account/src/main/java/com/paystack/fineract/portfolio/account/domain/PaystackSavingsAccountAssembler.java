package com.paystack.fineract.portfolio.account.domain;

import com.paystack.fineract.client.charge.service.ClientChargeOverrideReadService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.group.domain.Group;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountAssembler;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountCharge;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountChargeAssembler;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepositoryWrapper;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransactionDataSummaryWrapper;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransactionSummaryWrapper;
import org.apache.fineract.portfolio.savings.domain.SavingsProduct;
import org.apache.fineract.portfolio.savings.domain.SavingsProductRepository;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class PaystackSavingsAccountAssembler extends SavingsAccountAssembler {

    private final ClientChargeOverrideReadService clientChargeOverrideReadService;

    public PaystackSavingsAccountAssembler(final SavingsAccountTransactionSummaryWrapper savingsAccountTransactionSummaryWrapper,
            final SavingsAccountTransactionDataSummaryWrapper savingsAccountTransactionDataSummaryWrapper,
            final org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper clientRepository,
            final org.apache.fineract.portfolio.group.domain.GroupRepositoryWrapper groupRepository,
            final org.apache.fineract.organisation.staff.domain.StaffRepositoryWrapper staffRepository,
            final SavingsProductRepository savingProductRepository, final SavingsAccountRepositoryWrapper savingsAccountRepository,
            final SavingsAccountChargeAssembler savingsAccountChargeAssembler,
            final org.apache.fineract.infrastructure.core.serialization.FromJsonHelper fromApiJsonHelper,
            final org.apache.fineract.portfolio.account.service.AccountTransfersReadPlatformService accountTransfersReadPlatformService,
            final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
            final org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService configurationDomainService,
            final org.apache.fineract.infrastructure.core.service.ExternalIdFactory externalIdFactory,
            final ClientChargeOverrideReadService clientChargeOverrideReadService) {
        super(savingsAccountTransactionSummaryWrapper, savingsAccountTransactionDataSummaryWrapper, clientRepository, groupRepository,
                staffRepository, savingProductRepository, savingsAccountRepository, savingsAccountChargeAssembler, fromApiJsonHelper,
                accountTransfersReadPlatformService, jdbcTemplate, configurationDomainService, externalIdFactory);
        this.clientChargeOverrideReadService = clientChargeOverrideReadService;
    }

    @Override
    public SavingsAccount assembleFrom(final JsonCommand command, final AppUser submittedBy) {
        SavingsAccount account = super.assembleFrom(command, submittedBy);
        Long clientId = command.longValueOfParameterNamed("clientId");
        if (clientId != null) {
            applyClientOverridesToCharges(account, clientId);
        }
        appendProductCharges(account);
        return account;
    }

    /**
     * Append any product level charges removed during account creation; flag them as inactive
     *
     * @param account
     */
    private void appendProductCharges(SavingsAccount account) {
        SavingsProduct product = account.savingsProduct();
        Set<Charge> productCharges = product.charges();
        Set<SavingsAccountCharge> accountCharges = account.charges();

        // Get the current date to use for inactivation
        LocalDate currentDate = DateUtils.getBusinessLocalDate();

        // For each charge in the product, check if it exists in the account
        for (Charge productCharge : productCharges) {
            boolean chargeExists = false;

            // Check if this product charge already exists in the account
            for (SavingsAccountCharge accountCharge : accountCharges) {
                if (accountCharge.getCharge().getId().equals(productCharge.getId())) {
                    chargeExists = true;
                    break;
                }
            }

            // If the charge doesn't exist in the account, add it and mark it as inactive
            if (!chargeExists) {
                // Create a new SavingsAccountCharge
                ChargeTimeType chargeTime = ChargeTimeType.fromInt(productCharge.getChargeTimeType());
                ChargeCalculationType chargeCalculation = ChargeCalculationType.fromInt(productCharge.getChargeCalculation());
                BigDecimal amount = productCharge.getAmount();
                LocalDate dueDate = account.getActivationDate() != null ? account.getActivationDate() : account.getSubmittedOnDate();
                boolean isActive = true; // Initially active, will be inactivated later
                MonthDay feeOnMonthDay = productCharge.getFeeOnMonthDay();
                Integer feeInterval = productCharge.feeInterval();

                // Create a new charge without associating it with the account yet
                SavingsAccountCharge newCharge = SavingsAccountCharge.createNewWithoutSavingsAccount(productCharge, amount, chargeTime,
                        chargeCalculation, dueDate, isActive, feeOnMonthDay, feeInterval);

                // Add the charge to the account
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy");
                newCharge.update(account);
                // Mark the charge as inactive
                newCharge.inactiavateCharge(currentDate);
                account.addCharge(formatter, newCharge, productCharge);
            }
        }
    }

    @Override
    public SavingsAccount assembleFrom(final Client client, final Group group, final Long productId, final LocalDate appliedonDate,
            final AppUser appliedBy) {
        SavingsAccount account = super.assembleFrom(client, group, productId, appliedonDate, appliedBy);
        if (client != null) {
            applyClientOverridesToCharges(account, client.getId());
        }
        appendProductCharges(account);
        return account;
    }

    private void applyClientOverridesToCharges(SavingsAccount account, Long clientId) {
        for (SavingsAccountCharge sac : account.charges()) {
            org.apache.fineract.portfolio.charge.domain.Charge chargeDef = sac.getCharge();
            Integer calc = chargeDef.getChargeCalculation();
            if (calc == null) {
                continue;
            }
            ChargeCalculationType type = ChargeCalculationType.fromInt(calc);
            if (type.isPercentageOfAmount()) {
                BigDecimal pct = clientChargeOverrideReadService.resolvePrimaryAmount(clientId, chargeDef, null);
                sac.update(pct, sac.getDueDate(), null, null);
            } else if (type.isFlat()) {
                BigDecimal flat = clientChargeOverrideReadService.resolvePrimaryAmount(clientId, chargeDef, sac.amount());
                sac.update(flat, sac.getDueDate(), null, null);
            }
        }
    }
}
