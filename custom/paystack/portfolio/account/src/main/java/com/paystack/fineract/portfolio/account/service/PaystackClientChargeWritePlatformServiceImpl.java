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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.domain.ExternalId;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.ExternalIdFactory;
import org.apache.fineract.organisation.holiday.domain.HolidayRepositoryWrapper;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.workingdays.domain.WorkingDaysRepositoryWrapper;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeRepositoryWrapper;
import org.apache.fineract.portfolio.client.api.ClientApiConstants;
import org.apache.fineract.portfolio.client.data.ClientChargeDataValidator;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientCharge;
import org.apache.fineract.portfolio.client.domain.ClientChargePaidBy;
import org.apache.fineract.portfolio.client.domain.ClientChargeRepositoryWrapper;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.client.domain.ClientTransaction;
import org.apache.fineract.portfolio.client.domain.ClientTransactionRepository;
import org.apache.fineract.portfolio.client.service.ClientChargeWritePlatformService;
import org.apache.fineract.portfolio.paymentdetail.domain.PaymentDetail;
import org.apache.fineract.portfolio.paymentdetail.service.PaymentDetailWritePlatformService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Primary
@RequiredArgsConstructor
@Slf4j
public class PaystackClientChargeWritePlatformServiceImpl implements ClientChargeWritePlatformService {

    private final ChargeRepositoryWrapper chargeRepository;
    private final ClientRepositoryWrapper clientRepository;
    private final ClientChargeDataValidator clientChargeDataValidator;
    private final ConfigurationDomainService configurationDomainService;
    private final HolidayRepositoryWrapper holidayRepository;
    private final WorkingDaysRepositoryWrapper workingDaysRepository;
    private final ClientChargeRepositoryWrapper clientChargeRepository;
    private final ClientTransactionRepository clientTransactionRepository;
    private final PaymentDetailWritePlatformService paymentDetailWritePlatformService;
    private final JournalEntryWritePlatformService journalEntryWritePlatformService;
    private final FeeSplitService feeSplitService;

    @Override
    public CommandProcessingResult addCharge(Long clientId, JsonCommand command) {
        // Delegate to the original implementation for now
        // This could be extended later if needed
        return null; // TODO: Implement or delegate to original service
    }

    @Override
    @Transactional
    public CommandProcessingResult payCharge(Long clientId, Long clientChargeId, JsonCommand command) {
        try {
            this.clientChargeDataValidator.validatePayCharge(command.json());

            final Client client = this.clientRepository.getActiveClientInUserScope(clientId);
            final ClientCharge clientCharge = this.clientChargeRepository.findOneWithNotFoundDetection(clientChargeId);

            final Locale locale = command.extractLocale();
            final DateTimeFormatter fmt = DateTimeFormatter.ofPattern(command.dateFormat()).withLocale(locale);
            final LocalDate transactionDate = command.localDateValueOfParameterNamed(ClientApiConstants.transactionDateParamName);
            final BigDecimal amountPaid = command.bigDecimalValueOfParameterNamed(ClientApiConstants.amountParamName);
            final ExternalId transactionExternalId = ExternalIdFactory
                    .produce(command.stringValueOfParameterNamedAllowingNull(ClientApiConstants.externalIdParamName));
            final Money chargePaid = Money.of(clientCharge.getCurrency(), amountPaid);

            // Validate business rules for payment
            validatePaymentTransaction(client, clientCharge, fmt, transactionDate, amountPaid);

            // pay the charge
            clientCharge.pay(chargePaid);

            // create Payment Transaction
            final Map<String, Object> changes = new LinkedHashMap<>();
            final PaymentDetail paymentDetail = this.paymentDetailWritePlatformService.createAndPersistPaymentDetail(command, changes);

            ClientTransaction clientTransaction = ClientTransaction.payCharge(client, client.getOffice(), paymentDetail, transactionDate,
                    chargePaid, clientCharge.getCurrency().getCode(), transactionExternalId);
            this.clientTransactionRepository.saveAndFlush(clientTransaction);

            // update charge paid by associations
            final ClientChargePaidBy chargePaidBy = ClientChargePaidBy.instance(clientTransaction, clientCharge, amountPaid);
            clientTransaction.getClientChargePaidByCollection().add(chargePaidBy);

            // generate accounting entries
            generateAccountingEntries(clientTransaction);

            // Process fee split if enabled for this charge
            if (clientCharge.getCharge().isEnableFeeSplit()) {
                feeSplitService.processFeeSplit(clientTransaction, amountPaid);
            }

            return new CommandProcessingResultBuilder() //
                    .withTransactionId(clientTransaction.getId().toString())//
                    .withEntityId(clientCharge.getId()) //
                    .withSubEntityId(clientTransaction.getId()).withSubEntityExternalId(clientTransaction.getExternalId())
                    .withOfficeId(clientCharge.getClient().getOffice().getId()) //
                    .withClientId(clientCharge.getClient().getId()).build();
        } catch (final Exception e) {
            log.error("Error processing client charge payment", e);
            throw e;
        }
    }

    @Override
    public CommandProcessingResult waiveCharge(Long clientId, Long clientChargeId) {
        // Delegate to the original implementation for now
        return null; // TODO: Implement or delegate to original service
    }

    @Override
    public CommandProcessingResult deleteCharge(Long clientId, Long clientChargeId) {
        // Delegate to the original implementation for now
        return null; // TODO: Implement or delegate to original service
    }

    private void generateAccountingEntries(ClientTransaction clientTransaction) {
        Map<String, Object> accountingBridgeData = clientTransaction.toMapData();
        journalEntryWritePlatformService.createJournalEntriesForClientTransactions(accountingBridgeData);
    }

    private void validatePaymentTransaction(Client client, ClientCharge clientCharge, DateTimeFormatter fmt, 
                                          LocalDate transactionDate, BigDecimal amountPaid) {
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors)
                .resource(ClientApiConstants.CLIENT_CHARGES_RESOURCE_NAME);

        if (clientCharge.isNotActive()) {
            baseDataValidator.reset().failWithCodeNoParameterAddedToErrorCode("charge.is.not.active");
            if (!dataValidationErrors.isEmpty()) {
                throw new PlatformApiDataValidationException(dataValidationErrors);
            }
        }

        if (DateUtils.isBefore(transactionDate, client.getActivationDate())) {
            baseDataValidator.reset().parameter(ClientApiConstants.transactionDateParamName).value(transactionDate.format(fmt))
                    .failWithCodeNoParameterAddedToErrorCode("transaction.before.activationDate");
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }

        if (DateUtils.isDateInTheFuture(transactionDate)) {
            baseDataValidator.reset().parameter(ClientApiConstants.transactionDateParamName).value(transactionDate.format(fmt))
                    .failWithCodeNoParameterAddedToErrorCode("transaction.is.futureDate");
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }

        // validate charge is not already paid or waived
        if (clientCharge.isWaived()) {
            baseDataValidator.reset().failWithCodeNoParameterAddedToErrorCode("transaction.invalid.account.charge.is.already.waived");
            if (!dataValidationErrors.isEmpty()) {
                throw new PlatformApiDataValidationException(dataValidationErrors);
            }
        } else if (clientCharge.isPaid()) {
            baseDataValidator.reset().failWithCodeNoParameterAddedToErrorCode("transaction.invalid.account.charge.is.paid");
            if (!dataValidationErrors.isEmpty()) {
                throw new PlatformApiDataValidationException(dataValidationErrors);
            }
        }

        // validate amount is not more than total due
        if (amountPaid.compareTo(clientCharge.getAmountOutstanding().getAmount()) > 0) {
            baseDataValidator.reset().parameter(ClientApiConstants.amountParamName).value(amountPaid)
                    .failWithCodeNoParameterAddedToErrorCode("transaction.invalid.charge.amount.paid.in.access");
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }
    }

    @Override
    @SuppressWarnings("unused")
    public CommandProcessingResult updateCharge(Long clientId, JsonCommand command) {
        // functionality not yet supported
        return null;
    }

    @Override
    @SuppressWarnings("unused")
    public CommandProcessingResult inactivateCharge(Long clientId, Long clientChargeId) {
        // functionality not yet supported
        return null;
    }
}
