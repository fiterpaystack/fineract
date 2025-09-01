package com.paystack.fineract.accounting.journalentry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.accounting.closure.domain.GLClosure;
import org.apache.fineract.accounting.common.AccountingConstants;
import org.apache.fineract.accounting.journalentry.data.ChargePaymentDTO;
import org.apache.fineract.accounting.journalentry.data.SavingsDTO;
import org.apache.fineract.accounting.journalentry.data.SavingsTransactionDTO;
import org.apache.fineract.accounting.journalentry.service.AccountingProcessorHelper;
import org.apache.fineract.accounting.journalentry.service.AccrualBasedAccountingProcessorForSavings;
import org.apache.fineract.organisation.office.domain.Office;

public class PaystackAccrualBasedAccountingProcessorForSavings extends AccrualBasedAccountingProcessorForSavings {

    private AccountingProcessorHelper helper;

    public PaystackAccrualBasedAccountingProcessorForSavings(AccountingProcessorHelper helper) {
        super(helper);
        this.helper = helper;
    }

    @Override
    public void createJournalEntriesForSavings(final SavingsDTO savingsDTO) {
        final GLClosure latestGLClosure = this.helper.getLatestClosureByBranch(savingsDTO.getOfficeId());
        final Long savingsProductId = savingsDTO.getSavingsProductId();
        final Long savingsId = savingsDTO.getSavingsId();
        final String currencyCode = savingsDTO.getCurrencyCode();
        for (final SavingsTransactionDTO savingsTransactionDTO : savingsDTO.getNewSavingsTransactions()) {
            final LocalDate transactionDate = savingsTransactionDTO.getTransactionDate();
            final String transactionId = savingsTransactionDTO.getTransactionId();
            final Office office = this.helper.getOfficeById(savingsTransactionDTO.getOfficeId());
            final Long paymentTypeId = savingsTransactionDTO.getPaymentTypeId();
            final boolean isReversal = savingsTransactionDTO.isReversed();
            final BigDecimal amount = savingsTransactionDTO.getAmount();
            final BigDecimal overdraftAmount = savingsTransactionDTO.getOverdraftAmount();
            final List<ChargePaymentDTO> feePayments = savingsTransactionDTO.getFeePayments();
            final List<ChargePaymentDTO> penaltyPayments = savingsTransactionDTO.getPenaltyPayments();

            this.helper.checkForBranchClosures(latestGLClosure, transactionDate);

            if (savingsTransactionDTO.getTransactionType().isWithdrawal() && savingsTransactionDTO.isOverdraftTransaction()) {
                boolean isPositive = amount.subtract(overdraftAmount).compareTo(BigDecimal.ZERO) > 0;
                if (savingsTransactionDTO.isAccountTransfer()) {
                    this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                            AccountingConstants.AccrualAccountsForSavings.OVERDRAFT_PORTFOLIO_CONTROL.getValue(),
                            AccountingConstants.FinancialActivity.LIABILITY_TRANSFER.getValue(), savingsProductId, paymentTypeId, savingsId,
                            transactionId, transactionDate, overdraftAmount, isReversal);
                    if (isPositive) {
                        this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                                AccountingConstants.AccrualAccountsForSavings.SAVINGS_CONTROL.getValue(),
                                AccountingConstants.FinancialActivity.LIABILITY_TRANSFER.getValue(), savingsProductId, paymentTypeId,
                                savingsId, transactionId, transactionDate, amount.subtract(overdraftAmount), isReversal);
                    }
                } else {
                    this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                            AccountingConstants.AccrualAccountsForSavings.OVERDRAFT_PORTFOLIO_CONTROL.getValue(),
                            AccountingConstants.AccrualAccountsForSavings.SAVINGS_REFERENCE.getValue(), savingsProductId, paymentTypeId,
                            savingsId, transactionId, transactionDate, overdraftAmount, isReversal);
                    if (isPositive) {
                        this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                                AccountingConstants.AccrualAccountsForSavings.SAVINGS_CONTROL.getValue(),
                                AccountingConstants.AccrualAccountsForSavings.SAVINGS_REFERENCE.getValue(), savingsProductId, paymentTypeId,
                                savingsId, transactionId, transactionDate, amount.subtract(overdraftAmount), isReversal);
                    }
                }
            }

            else if (savingsTransactionDTO.getTransactionType().isDeposit() && savingsTransactionDTO.isOverdraftTransaction()) {
                boolean isPositive = amount.subtract(overdraftAmount).compareTo(BigDecimal.ZERO) > 0;
                if (savingsTransactionDTO.isAccountTransfer()) {
                    this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                            AccountingConstants.FinancialActivity.LIABILITY_TRANSFER.getValue(),
                            AccountingConstants.AccrualAccountsForSavings.OVERDRAFT_PORTFOLIO_CONTROL.getValue(), savingsProductId,
                            paymentTypeId, savingsId, transactionId, transactionDate, overdraftAmount, isReversal);
                    if (isPositive) {
                        this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                                AccountingConstants.FinancialActivity.LIABILITY_TRANSFER.getValue(),
                                AccountingConstants.AccrualAccountsForSavings.SAVINGS_CONTROL.getValue(), savingsProductId, paymentTypeId,
                                savingsId, transactionId, transactionDate, amount.subtract(overdraftAmount), isReversal);
                    }
                } else {
                    this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                            AccountingConstants.AccrualAccountsForSavings.SAVINGS_REFERENCE.getValue(),
                            AccountingConstants.AccrualAccountsForSavings.OVERDRAFT_PORTFOLIO_CONTROL.getValue(), savingsProductId,
                            paymentTypeId, savingsId, transactionId, transactionDate, overdraftAmount, isReversal);
                    if (isPositive) {
                        this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                                AccountingConstants.AccrualAccountsForSavings.SAVINGS_REFERENCE.getValue(),
                                AccountingConstants.AccrualAccountsForSavings.SAVINGS_CONTROL.getValue(), savingsProductId, paymentTypeId,
                                savingsId, transactionId, transactionDate, amount.subtract(overdraftAmount), isReversal);
                    }
                }
            }

            /** Handle Deposits and reversals of deposits **/
            else if (savingsTransactionDTO.getTransactionType().isDeposit()) {
                if (savingsTransactionDTO.isAccountTransfer()) {
                    this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                            AccountingConstants.FinancialActivity.LIABILITY_TRANSFER.getValue(),
                            AccountingConstants.AccrualAccountsForSavings.SAVINGS_CONTROL.getValue(), savingsProductId, paymentTypeId,
                            savingsId, transactionId, transactionDate, amount, isReversal);
                } else {
                    this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                            AccountingConstants.AccrualAccountsForSavings.SAVINGS_REFERENCE.getValue(),
                            AccountingConstants.AccrualAccountsForSavings.SAVINGS_CONTROL.getValue(), savingsProductId, paymentTypeId,
                            savingsId, transactionId, transactionDate, amount, isReversal);
                }
            }

            /** Handle Deposits and reversals of Dividend pay outs **/
            else if (savingsTransactionDTO.getTransactionType().isDividendPayout()) {
                this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                        AccountingConstants.FinancialActivity.PAYABLE_DIVIDENDS.getValue(),
                        AccountingConstants.AccrualAccountsForSavings.SAVINGS_CONTROL.getValue(), savingsProductId, paymentTypeId,
                        savingsId, transactionId, transactionDate, amount, isReversal);
            }

            /** Handle withdrawals and reversals of withdrawals **/
            else if (savingsTransactionDTO.getTransactionType().isWithdrawal()) {
                if (savingsTransactionDTO.isAccountTransfer()) {
                    this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                            AccountingConstants.AccrualAccountsForSavings.SAVINGS_CONTROL.getValue(),
                            AccountingConstants.FinancialActivity.LIABILITY_TRANSFER.getValue(), savingsProductId, paymentTypeId, savingsId,
                            transactionId, transactionDate, amount, isReversal);
                } else {
                    this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                            AccountingConstants.AccrualAccountsForSavings.SAVINGS_CONTROL.getValue(),
                            AccountingConstants.AccrualAccountsForSavings.SAVINGS_REFERENCE.getValue(), savingsProductId, paymentTypeId,
                            savingsId, transactionId, transactionDate, amount, isReversal);
                }
            }

            else if (savingsTransactionDTO.getTransactionType().isEscheat()) {
                this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                        AccountingConstants.AccrualAccountsForSavings.SAVINGS_CONTROL.getValue(),
                        AccountingConstants.AccrualAccountsForSavings.ESCHEAT_LIABILITY.getValue(), savingsProductId, paymentTypeId,
                        savingsId, transactionId, transactionDate, amount, isReversal);
            }
            /**
             * Handle Interest Applications and reversals of Interest Applications
             **/
            else if (savingsTransactionDTO.getTransactionType().isInterestPosting() && savingsTransactionDTO.isOverdraftTransaction()) {
                boolean isPositive = amount.subtract(overdraftAmount).compareTo(BigDecimal.ZERO) > 0;
                // Post journal entry if earned interest amount is greater than
                // zero
                if (savingsTransactionDTO.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                    this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                            AccountingConstants.AccrualAccountsForSavings.INTEREST_ON_SAVINGS.getValue(),
                            AccountingConstants.AccrualAccountsForSavings.OVERDRAFT_PORTFOLIO_CONTROL.getValue(), savingsProductId,
                            paymentTypeId, savingsId, transactionId, transactionDate, overdraftAmount, isReversal);
                    if (isPositive) {
                        this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                                AccountingConstants.AccrualAccountsForSavings.INTEREST_ON_SAVINGS.getValue(),
                                AccountingConstants.AccrualAccountsForSavings.SAVINGS_CONTROL.getValue(), savingsProductId, paymentTypeId,
                                savingsId, transactionId, transactionDate, amount.subtract(overdraftAmount), isReversal);
                    }
                }
            }

            else if (savingsTransactionDTO.getTransactionType().isInterestPosting()) {
                // Post journal entry if earned interest amount is greater than
                // zero
                if (savingsTransactionDTO.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                    this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                            AccountingConstants.AccrualAccountsForSavings.INTEREST_PAYABLE.getValue(),
                            AccountingConstants.AccrualAccountsForSavings.SAVINGS_CONTROL.getValue(), savingsProductId, paymentTypeId,
                            savingsId, transactionId, transactionDate, amount, isReversal);
                }
            }

            else if (savingsTransactionDTO.getTransactionType().isAccrual()) {
                // Post journal entry for Accrual Recognition
                if (savingsTransactionDTO.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                    this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                            AccountingConstants.AccrualAccountsForSavings.INTEREST_ON_SAVINGS.getValue(),
                            AccountingConstants.AccrualAccountsForSavings.INTEREST_PAYABLE.getValue(), savingsProductId, paymentTypeId,
                            savingsId, transactionId, transactionDate, amount, isReversal);
                }
            }

            else if (savingsTransactionDTO.getTransactionType().isWithholdTax()) {
                this.helper.createAccrualBasedJournalEntriesAndReversalsForSavingsTax(office, currencyCode,
                        AccountingConstants.AccrualAccountsForSavings.SAVINGS_CONTROL,
                        AccountingConstants.AccrualAccountsForSavings.SAVINGS_REFERENCE, savingsProductId, paymentTypeId, savingsId,
                        transactionId, transactionDate, amount, isReversal, savingsTransactionDTO.getTaxPayments());
            }

            /** Handle Fees Deductions and reversals of Fees Deductions **/
            else if (savingsTransactionDTO.getTransactionType().isFeeDeduction() && savingsTransactionDTO.isOverdraftTransaction()) {
                boolean isPositive = amount.subtract(overdraftAmount).compareTo(BigDecimal.ZERO) > 0;
                // Is the Charge a penalty?
                if (penaltyPayments.size() > 0) {
                    this.helper.createAccrualBasedJournalEntriesAndReversalsForSavingsCharges(office, currencyCode,
                            AccountingConstants.AccrualAccountsForSavings.OVERDRAFT_PORTFOLIO_CONTROL,
                            AccountingConstants.AccrualAccountsForSavings.INCOME_FROM_PENALTIES, savingsProductId, paymentTypeId, savingsId,
                            transactionId, transactionDate, overdraftAmount, isReversal, penaltyPayments);
                    if (isPositive) {
                        this.helper.createAccrualBasedJournalEntriesAndReversalsForSavingsCharges(office, currencyCode,
                                AccountingConstants.AccrualAccountsForSavings.SAVINGS_CONTROL,
                                AccountingConstants.AccrualAccountsForSavings.INCOME_FROM_PENALTIES, savingsProductId, paymentTypeId,
                                savingsId, transactionId, transactionDate, amount.subtract(overdraftAmount), isReversal, penaltyPayments);
                    }
                } else {
                    this.helper.createAccrualBasedJournalEntriesAndReversalsForSavingsCharges(office, currencyCode,
                            AccountingConstants.AccrualAccountsForSavings.OVERDRAFT_PORTFOLIO_CONTROL,
                            AccountingConstants.AccrualAccountsForSavings.INCOME_FROM_FEES, savingsProductId, paymentTypeId, savingsId,
                            transactionId, transactionDate, overdraftAmount, isReversal, feePayments);
                    if (isPositive) {
                        this.helper.createAccrualBasedJournalEntriesAndReversalsForSavingsCharges(office, currencyCode,
                                AccountingConstants.AccrualAccountsForSavings.SAVINGS_CONTROL,
                                AccountingConstants.AccrualAccountsForSavings.INCOME_FROM_FEES, savingsProductId, paymentTypeId, savingsId,
                                transactionId, transactionDate, amount.subtract(overdraftAmount), isReversal, feePayments);
                    }
                }
            }

            else if (savingsTransactionDTO.getTransactionType().isFeeDeduction()) {
                // Is the Charge a penalty?
                if (penaltyPayments.size() > 0) {
                    this.helper.createAccrualBasedJournalEntriesAndReversalsForSavingsCharges(office, currencyCode,
                            AccountingConstants.AccrualAccountsForSavings.SAVINGS_CONTROL,
                            AccountingConstants.AccrualAccountsForSavings.INCOME_FROM_PENALTIES, savingsProductId, paymentTypeId, savingsId,
                            transactionId, transactionDate, amount, isReversal, penaltyPayments);
                } else {
                    this.helper.createAccrualBasedJournalEntriesAndReversalsForSavingsCharges(office, currencyCode,
                            AccountingConstants.AccrualAccountsForSavings.SAVINGS_CONTROL,
                            AccountingConstants.AccrualAccountsForSavings.INCOME_FROM_FEES, savingsProductId, paymentTypeId, savingsId,
                            transactionId, transactionDate, amount, isReversal, feePayments);
                }
            }

            /** Handle Transfers proposal **/
            else if (savingsTransactionDTO.getTransactionType().isInitiateTransfer()) {
                this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                        AccountingConstants.AccrualAccountsForSavings.SAVINGS_CONTROL.getValue(),
                        AccountingConstants.AccrualAccountsForSavings.TRANSFERS_SUSPENSE.getValue(), savingsProductId, paymentTypeId,
                        savingsId, transactionId, transactionDate, amount, isReversal);
            }

            /** Handle Transfer Withdrawal or Acceptance **/
            else if (savingsTransactionDTO.getTransactionType().isWithdrawTransfer()
                    || savingsTransactionDTO.getTransactionType().isApproveTransfer()) {
                this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                        AccountingConstants.AccrualAccountsForSavings.TRANSFERS_SUSPENSE.getValue(),
                        AccountingConstants.AccrualAccountsForSavings.SAVINGS_CONTROL.getValue(), savingsProductId, paymentTypeId,
                        savingsId, transactionId, transactionDate, amount, isReversal);
            }

            /** overdraft **/
            else if (savingsTransactionDTO.getTransactionType().isOverdraftInterest()) {
                this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                        AccountingConstants.AccrualAccountsForSavings.SAVINGS_REFERENCE.getValue(),
                        AccountingConstants.AccrualAccountsForSavings.INCOME_FROM_INTEREST.getValue(), savingsProductId, paymentTypeId,
                        savingsId, transactionId, transactionDate, amount, isReversal);
            } else if (savingsTransactionDTO.getTransactionType().isWrittenoff()) {
                this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                        AccountingConstants.AccrualAccountsForSavings.LOSSES_WRITTEN_OFF.getValue(),
                        AccountingConstants.AccrualAccountsForSavings.OVERDRAFT_PORTFOLIO_CONTROL.getValue(), savingsProductId,
                        paymentTypeId, savingsId, transactionId, transactionDate, amount, isReversal);
            } else if (savingsTransactionDTO.getTransactionType().isOverdraftFee()) {
                this.helper.createAccrualBasedJournalEntriesAndReversalsForSavingsCharges(office, currencyCode,
                        AccountingConstants.AccrualAccountsForSavings.SAVINGS_REFERENCE,
                        AccountingConstants.AccrualAccountsForSavings.INCOME_FROM_FEES, savingsProductId, paymentTypeId, savingsId,
                        transactionId, transactionDate, amount, isReversal, feePayments);
            } else if (savingsTransactionDTO.getTransactionType().isVatOnFees()) {
                this.helper.createAccrualBasedJournalEntriesAndReversalsForSavingsTax(office, currencyCode,
                        AccountingConstants.AccrualAccountsForSavings.SAVINGS_CONTROL,
                        AccountingConstants.AccrualAccountsForSavings.SAVINGS_REFERENCE, savingsProductId, paymentTypeId, savingsId,
                        transactionId, transactionDate, amount, isReversal, savingsTransactionDTO.getTaxPayments());
            } else if (savingsTransactionDTO.getTransactionType().isEmtLevy() && savingsTransactionDTO.isOverdraftTransaction()) {
                boolean isPositive = amount.subtract(overdraftAmount).compareTo(BigDecimal.ZERO) > 0;
                this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                        AccountingConstants.AccrualAccountsForSavings.OVERDRAFT_PORTFOLIO_CONTROL.getValue(),
                        AccountingConstants.FinancialActivity.EMT_LEVY.getValue(), savingsProductId, paymentTypeId, savingsId,
                        transactionId, transactionDate, overdraftAmount, isReversal);
                if (isPositive) {
                    this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                            AccountingConstants.AccrualAccountsForSavings.SAVINGS_CONTROL.getValue(),
                            AccountingConstants.FinancialActivity.EMT_LEVY.getValue(), savingsProductId, paymentTypeId, savingsId,
                            transactionId, transactionDate, amount.subtract(overdraftAmount), isReversal);
                }
            } else if (savingsTransactionDTO.getTransactionType().isEmtLevy()) {
                this.helper.createCashBasedJournalEntriesAndReversalsForSavings(office, currencyCode,
                        AccountingConstants.AccrualAccountsForSavings.SAVINGS_CONTROL.getValue(),
                        AccountingConstants.FinancialActivity.EMT_LEVY.getValue(), savingsProductId, paymentTypeId, savingsId,
                        transactionId, transactionDate, amount, isReversal);
            }
        }
    }
}
