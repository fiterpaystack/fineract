package com.paystack.fineract.portfolio.account.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.domain.ExternalId;
import org.apache.fineract.infrastructure.core.service.ExternalIdFactory;
import org.apache.fineract.portfolio.account.PortfolioAccountType;
import org.apache.fineract.portfolio.account.data.AccountTransfersDataValidator;
import org.apache.fineract.portfolio.account.domain.AccountTransferAssembler;
import org.apache.fineract.portfolio.account.domain.AccountTransferDetailRepository;
import org.apache.fineract.portfolio.account.domain.AccountTransferDetails;
import org.apache.fineract.portfolio.account.domain.AccountTransferRepository;
import org.apache.fineract.portfolio.account.exception.DifferentCurrenciesException;
import org.apache.fineract.portfolio.account.service.AccountTransfersWritePlatformServiceImpl;
import org.apache.fineract.portfolio.loanaccount.data.HolidayDetailDTO;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanAccountDomainService;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionType;
import org.apache.fineract.portfolio.loanaccount.service.LoanAssembler;
import org.apache.fineract.portfolio.loanaccount.service.LoanReadPlatformService;
import org.apache.fineract.portfolio.paymentdetail.domain.PaymentDetail;
import org.apache.fineract.portfolio.savings.SavingsTransactionBooleanValues;
import org.apache.fineract.portfolio.savings.domain.GSIMRepositoy;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountAssembler;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountCharge;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountChargePaidBy;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction;
import org.apache.fineract.portfolio.savings.service.SavingsAccountDomainService;
import org.apache.fineract.portfolio.savings.service.SavingsAccountWritePlatformService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.apache.fineract.portfolio.account.AccountDetailConstants.fromAccountIdParamName;
import static org.apache.fineract.portfolio.account.AccountDetailConstants.fromAccountTypeParamName;
import static org.apache.fineract.portfolio.account.AccountDetailConstants.toAccountIdParamName;
import static org.apache.fineract.portfolio.account.AccountDetailConstants.toAccountTypeParamName;
import static org.apache.fineract.portfolio.account.api.AccountTransfersApiConstants.transferAmountParamName;
import static org.apache.fineract.portfolio.account.api.AccountTransfersApiConstants.transferDateParamName;

@Service
@Primary
@Slf4j
public class CustomAccountTransferWritePlatformServiceImpl extends AccountTransfersWritePlatformServiceImpl {


    @Autowired
    private SavingsVatPostProcessorService vatPostProcessorService;

    public CustomAccountTransferWritePlatformServiceImpl(AccountTransfersDataValidator accountTransfersDataValidator, AccountTransferAssembler accountTransferAssembler, AccountTransferRepository accountTransferRepository, SavingsAccountAssembler savingsAccountAssembler, SavingsAccountDomainService savingsAccountDomainService, LoanAssembler loanAccountAssembler, LoanAccountDomainService loanAccountDomainService, SavingsAccountWritePlatformService savingsAccountWritePlatformService, AccountTransferDetailRepository accountTransferDetailRepository, LoanReadPlatformService loanReadPlatformService, GSIMRepositoy gsimRepository, ConfigurationDomainService configurationDomainService, ExternalIdFactory externalIdFactory, FineractProperties fineractProperties) {
        super(accountTransfersDataValidator, accountTransferAssembler, accountTransferRepository, savingsAccountAssembler, savingsAccountDomainService, loanAccountAssembler, loanAccountDomainService, savingsAccountWritePlatformService, accountTransferDetailRepository, loanReadPlatformService, gsimRepository, configurationDomainService, externalIdFactory, fineractProperties);
    }


    @Transactional
    @Override
    public CommandProcessingResult create(final JsonCommand command) {
        boolean isRegularTransaction = true;

        this.accountTransfersDataValidator.validate(command);

        final LocalDate transactionDate = command.localDateValueOfParameterNamed(transferDateParamName);
        final BigDecimal transactionAmount = command.bigDecimalValueOfParameterNamed(transferAmountParamName);

        final Locale locale = command.extractLocale();
        final DateTimeFormatter fmt = DateTimeFormatter.ofPattern(command.dateFormat()).withLocale(locale);

        final Integer fromAccountTypeId = command.integerValueSansLocaleOfParameterNamed(fromAccountTypeParamName);
        final PortfolioAccountType fromAccountType = PortfolioAccountType.fromInt(fromAccountTypeId);

        final Integer toAccountTypeId = command.integerValueSansLocaleOfParameterNamed(toAccountTypeParamName);
        final PortfolioAccountType toAccountType = PortfolioAccountType.fromInt(toAccountTypeId);

        final PaymentDetail paymentDetail = null;
        Long fromSavingsAccountId = null;
        Long transferDetailId = null;
        boolean isInterestTransfer = false;
        boolean isAccountTransfer = true;
        Long fromLoanAccountId = null;
        boolean isWithdrawBalance = false;
        final boolean backdatedTxnsAllowedTill = false;

        if (isSavingsToSavingsAccountTransfer(fromAccountType, toAccountType)) {

            fromSavingsAccountId = command.longValueOfParameterNamed(fromAccountIdParamName);
            final SavingsAccount fromSavingsAccount = this.savingsAccountAssembler.assembleFrom(fromSavingsAccountId,
                    backdatedTxnsAllowedTill);

            final SavingsTransactionBooleanValues transactionBooleanValues = new SavingsTransactionBooleanValues(isAccountTransfer,
                    isRegularTransaction, fromSavingsAccount.isWithdrawalFeeApplicableForTransfer(), isInterestTransfer, isWithdrawBalance);


            List<SavingsVatPostProcessorService.FeeTransactionPair> feeTransactions =
                    new ArrayList<>();


            WithdrawalResult withdrawalResult = performWithdrawalWithFeeTracking(
                    fromSavingsAccount, fmt, transactionDate, transactionAmount,
                    paymentDetail, transactionBooleanValues, backdatedTxnsAllowedTill
            );

            if (!withdrawalResult.feeTransactions().isEmpty()) {
                List<SavingsAccountTransaction> vatTransactions =
                        vatPostProcessorService.processVatForMultipleFees(
                                withdrawalResult.feeTransactions(),
                                fromSavingsAccount
                        );

                // Update account balance if VAT was applied
                if (!vatTransactions.isEmpty()) {
                    BigDecimal totalVat = vatTransactions.stream()
                            .map(SavingsAccountTransaction::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    // The account balance is already updated by the VAT processor
                    log.info("Applied VAT of {} to transfer", totalVat);
                }
            }
            final Long toSavingsId = command.longValueOfParameterNamed(toAccountIdParamName);
            final SavingsAccount toSavingsAccount = this.savingsAccountAssembler.assembleFrom(toSavingsId, backdatedTxnsAllowedTill);

            final SavingsAccountTransaction deposit = this.savingsAccountDomainService.handleDeposit(toSavingsAccount, fmt, transactionDate,
                    transactionAmount, paymentDetail, isAccountTransfer, isRegularTransaction, backdatedTxnsAllowedTill);

            if (!fromSavingsAccount.getCurrency().getCode().equals(toSavingsAccount.getCurrency().getCode())) {
                throw new DifferentCurrenciesException(fromSavingsAccount.getCurrency().getCode(),
                        toSavingsAccount.getCurrency().getCode());
            }

            final AccountTransferDetails accountTransferDetails = this.accountTransferAssembler.assembleSavingsToSavingsTransfer(command,
                    fromSavingsAccount, toSavingsAccount, withdrawalResult.withdrawal, deposit);
            this.accountTransferDetailRepository.saveAndFlush(accountTransferDetails);
            transferDetailId = accountTransferDetails.getId();

        } else if (isSavingsToLoanAccountTransfer(fromAccountType, toAccountType)) {
            //
            fromSavingsAccountId = command.longValueOfParameterNamed(fromAccountIdParamName);
            final SavingsAccount fromSavingsAccount = this.savingsAccountAssembler.assembleFrom(fromSavingsAccountId,
                    backdatedTxnsAllowedTill);

            final SavingsTransactionBooleanValues transactionBooleanValues = new SavingsTransactionBooleanValues(isAccountTransfer,
                    isRegularTransaction, fromSavingsAccount.isWithdrawalFeeApplicableForTransfer(), isInterestTransfer, isWithdrawBalance);
            final SavingsAccountTransaction withdrawal = this.savingsAccountDomainService.handleWithdrawal(fromSavingsAccount, fmt,
                    transactionDate, transactionAmount, paymentDetail, transactionBooleanValues, backdatedTxnsAllowedTill);

            final Long toLoanAccountId = command.longValueOfParameterNamed(toAccountIdParamName);
            Loan toLoanAccount = this.loanAccountAssembler.assembleFrom(toLoanAccountId);

            final Boolean isHolidayValidationDone = false;
            final HolidayDetailDTO holidayDetailDto = null;
            final boolean isRecoveryRepayment = false;
            final String chargeRefundChargeType = null;

            ExternalId externalId = externalIdFactory.create();
            final LoanTransaction loanRepaymentTransaction = this.loanAccountDomainService.makeRepayment(LoanTransactionType.REPAYMENT,
                    toLoanAccount, transactionDate, transactionAmount, paymentDetail, null, externalId, isRecoveryRepayment,
                    chargeRefundChargeType, isAccountTransfer, holidayDetailDto, isHolidayValidationDone);
            toLoanAccount = loanRepaymentTransaction.getLoan();
            final AccountTransferDetails accountTransferDetails = this.accountTransferAssembler.assembleSavingsToLoanTransfer(command,
                    fromSavingsAccount, toLoanAccount, withdrawal, loanRepaymentTransaction);
            this.accountTransferDetailRepository.saveAndFlush(accountTransferDetails);
            transferDetailId = accountTransferDetails.getId();

        } else if (isLoanToSavingsAccountTransfer(fromAccountType, toAccountType)) {
            // FIXME - kw - ADD overpaid loan to savings account transfer
            // support.

            fromLoanAccountId = command.longValueOfParameterNamed(fromAccountIdParamName);
            final Loan fromLoanAccount = this.loanAccountAssembler.assembleFrom(fromLoanAccountId);
            ExternalId externalId = externalIdFactory.create();
            final LoanTransaction loanRefundTransaction = this.loanAccountDomainService.makeRefund(fromLoanAccountId,
                    new CommandProcessingResultBuilder(), transactionDate, transactionAmount, paymentDetail, null, externalId);

            final Long toSavingsAccountId = command.longValueOfParameterNamed(toAccountIdParamName);
            final SavingsAccount toSavingsAccount = this.savingsAccountAssembler.assembleFrom(toSavingsAccountId, backdatedTxnsAllowedTill);

            final SavingsAccountTransaction deposit = this.savingsAccountDomainService.handleDeposit(toSavingsAccount, fmt, transactionDate,
                    transactionAmount, paymentDetail, isAccountTransfer, isRegularTransaction, backdatedTxnsAllowedTill);

            final AccountTransferDetails accountTransferDetails = this.accountTransferAssembler.assembleLoanToSavingsTransfer(command,
                    fromLoanAccount, toSavingsAccount, deposit, loanRefundTransaction);
            this.accountTransferDetailRepository.saveAndFlush(accountTransferDetails);
            transferDetailId = accountTransferDetails.getId();

        }

        final CommandProcessingResultBuilder builder = new CommandProcessingResultBuilder().withEntityId(transferDetailId);

        if (fromAccountType.isSavingsAccount()) {
            builder.withSavingsId(fromSavingsAccountId);
        }
        if (fromAccountType.isLoanAccount()) {
            builder.withLoanId(fromLoanAccountId);
        }

        return builder.build();
    }



    /**
     * Wrapper method to perform withdrawal and track fee transactions
     */
    private WithdrawalResult performWithdrawalWithFeeTracking(
            SavingsAccount account,
            DateTimeFormatter fmt,
            LocalDate transactionDate,
            BigDecimal amount,
            PaymentDetail paymentDetail,
            SavingsTransactionBooleanValues transactionBooleanValues,
            boolean backdatedTxnsAllowedTill) {

        // Capture the current transaction count
        int txnCountBefore = account.getTransactions().size();

        // Perform the withdrawal (this will create fee transactions internally)
        SavingsAccountTransaction withdrawal = this.savingsAccountDomainService
                .handleWithdrawal(account, fmt, transactionDate, amount,
                        paymentDetail, transactionBooleanValues, backdatedTxnsAllowedTill);

        // Find fee transactions that were created
        List<SavingsVatPostProcessorService.FeeTransactionPair> feeTransactions =
                extractFeeTransactions(account, txnCountBefore);

        return new WithdrawalResult(withdrawal, feeTransactions);
    }

    /**
     * Extract fee transactions that were created during withdrawal
     */
    private List<SavingsVatPostProcessorService.FeeTransactionPair> extractFeeTransactions(
            SavingsAccount account, int txnCountBefore) {

        List<SavingsVatPostProcessorService.FeeTransactionPair> feeTransactions =
                new ArrayList<>();

        List<SavingsAccountTransaction> allTransactions =
                new ArrayList<>(account.getTransactions());

        // Get new transactions created after the withdrawal
        for (int i = txnCountBefore; i < allTransactions.size(); i++) {
            SavingsAccountTransaction txn = allTransactions.get(i);
            final SavingsAccountChargePaidBy chargePaidBy = txn.getSavingsAccountChargePaidBy();

            if (txn.isChargeTransaction()  && chargePaidBy != null) {
                // Find the corresponding charge
                SavingsAccountCharge charge = findChargeForTransaction(account, txn);
                if (charge != null) {
                    feeTransactions.add(
                            new SavingsVatPostProcessorService.FeeTransactionPair(txn, charge)
                    );
                }
            }
        }

        return feeTransactions;
    }

    private SavingsAccountCharge findChargeForTransaction(
            SavingsAccount account, SavingsAccountTransaction transaction) {
        // Match transaction to charge based on amount and date
        for (SavingsAccountCharge charge : account.charges()) {
            if (charge.isWithdrawalFee() && charge.isActive()) {
                // Simple matching logic - can be enhanced
                return charge;
            }
        }
        return null;
    }


    private record WithdrawalResult(SavingsAccountTransaction withdrawal,
                                        List<SavingsVatPostProcessorService.FeeTransactionPair> feeTransactions) {
}

}
