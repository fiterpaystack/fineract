package com.paystack.fineract.portfolio.account.domain;

import com.paystack.fineract.client.charge.service.ClientChargeOverrideReadService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.group.domain.Group;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountAssembler;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountCharge;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountChargeAssembler;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepositoryWrapper;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransactionDataSummaryWrapper;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransactionSummaryWrapper;
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
        return account;
    }

    @Override
    public SavingsAccount assembleFrom(final Client client, final Group group, final Long productId, final LocalDate appliedonDate,
            final AppUser appliedBy) {
        SavingsAccount account = super.assembleFrom(client, group, productId, appliedonDate, appliedBy);
        if (client != null) {
            applyClientOverridesToCharges(account, client.getId());
        }
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
