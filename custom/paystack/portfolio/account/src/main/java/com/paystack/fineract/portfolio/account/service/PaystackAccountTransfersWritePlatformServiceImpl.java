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
package com.paystack.fineract.portfolio.account.service;

import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.service.ExternalIdFactory;
import org.apache.fineract.portfolio.account.data.AccountTransferDTO;
import org.apache.fineract.portfolio.account.data.AccountTransfersDataValidator;
import org.apache.fineract.portfolio.account.domain.AccountTransferAssembler;
import org.apache.fineract.portfolio.account.domain.AccountTransferDetailRepository;
import org.apache.fineract.portfolio.account.domain.AccountTransferRepository;
import org.apache.fineract.portfolio.account.service.AccountTransfersWritePlatformServiceImpl;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanAccountDomainService;
import org.apache.fineract.portfolio.loanaccount.service.LoanAssembler;
import org.apache.fineract.portfolio.loanaccount.service.LoanReadPlatformService;
import org.apache.fineract.portfolio.savings.domain.GSIMRepositoy;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountAssembler;
import org.apache.fineract.portfolio.savings.service.SavingsAccountDomainService;
import org.apache.fineract.portfolio.savings.service.SavingsAccountWritePlatformService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Custom implementation of AccountTransfersWritePlatformService that sets transfer context for intra-client transfer
 * detection to exempt EMT Levy.
 */
@Service
@Primary
public class PaystackAccountTransfersWritePlatformServiceImpl extends AccountTransfersWritePlatformServiceImpl {

    public PaystackAccountTransfersWritePlatformServiceImpl(AccountTransfersDataValidator accountTransfersDataValidator,
            AccountTransferAssembler accountTransferAssembler, AccountTransferRepository accountTransferRepository,
            SavingsAccountAssembler savingsAccountAssembler, SavingsAccountDomainService savingsAccountDomainService,
            LoanAssembler loanAccountAssembler, LoanAccountDomainService loanAccountDomainService,
            SavingsAccountWritePlatformService savingsAccountWritePlatformService,
            AccountTransferDetailRepository accountTransferDetailRepository, LoanReadPlatformService loanReadPlatformService,
            GSIMRepositoy gsimRepository, ConfigurationDomainService configurationDomainService, ExternalIdFactory externalIdFactory,
            FineractProperties fineractProperties) {
        super(accountTransfersDataValidator, accountTransferAssembler, accountTransferRepository, savingsAccountAssembler,
                savingsAccountDomainService, loanAccountAssembler, loanAccountDomainService, savingsAccountWritePlatformService,
                accountTransferDetailRepository, loanReadPlatformService, gsimRepository, configurationDomainService, externalIdFactory,
                fineractProperties);
    }

    @Override
    @Transactional
    public CommandProcessingResult create(final JsonCommand command) {
        try {
            // Extract client IDs from command to detect intra-client transfers
            Long fromClientId = command.longValueOfParameterNamed("fromClientId");
            Long toClientId = command.longValueOfParameterNamed("toClientId");

            // Set transfer context for EMT Levy exemption detection
            if (fromClientId != null && toClientId != null) {
                PaystackSavingsAccountDomainServiceJpa.setTransferContext(fromClientId, toClientId);
            }

            try {
                return super.create(command);
            } finally {
                // Always clear the transfer context after processing
                PaystackSavingsAccountDomainServiceJpa.clearTransferContext();
            }
        } catch (Exception e) {
            // Ensure context is cleared even on error
            PaystackSavingsAccountDomainServiceJpa.clearTransferContext();
            throw e;
        }
    }

    @Override
    @Transactional
    public Long transferFunds(final AccountTransferDTO accountTransferDTO) {
        try {
            // Extract client IDs from transfer DTO to detect intra-client transfers
            Long fromClientId = getClientIdFromAccount(accountTransferDTO.getFromAccountType(), accountTransferDTO.getFromAccountId(),
                    accountTransferDTO.getFromSavingsAccount(), accountTransferDTO.getLoan());
            Long toClientId = getClientIdFromAccount(accountTransferDTO.getToAccountType(), accountTransferDTO.getToAccountId(),
                    accountTransferDTO.getToSavingsAccount(), accountTransferDTO.getToLoan());

            // Set transfer context for EMT Levy exemption detection
            if (fromClientId != null && toClientId != null) {
                PaystackSavingsAccountDomainServiceJpa.setTransferContext(fromClientId, toClientId);
            }

            try {
                return super.transferFunds(accountTransferDTO);
            } finally {
                // Always clear the transfer context after processing
                PaystackSavingsAccountDomainServiceJpa.clearTransferContext();
            }
        } catch (Exception e) {
            // Ensure context is cleared even on error
            PaystackSavingsAccountDomainServiceJpa.clearTransferContext();
            throw e;
        }
    }

    /**
     * Extract client ID from account based on account type
     */
    private Long getClientIdFromAccount(org.apache.fineract.portfolio.account.PortfolioAccountType accountType, Long accountId,
            SavingsAccount savingsAccount, Loan loan) {
        if (accountType.isSavingsAccount()) {
            if (savingsAccount != null) {
                return savingsAccount.clientId();
            }
            // If account not provided, we can't determine client ID here
            // The context will be set to null and EMT Levy will apply normally
            return null;
        } else if (accountType.isLoanAccount()) {
            if (loan != null) {
                return loan.getClientId();
            }
            return null;
        }
        return null;
    }
}
