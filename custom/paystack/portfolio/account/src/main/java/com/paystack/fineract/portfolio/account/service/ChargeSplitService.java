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

import com.paystack.fineract.portfolio.account.data.ChargeSplitData;
import com.paystack.fineract.portfolio.account.domain.ChargeSplit;
import com.paystack.fineract.portfolio.account.domain.ChargeSplitRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.glaccount.domain.GLAccountRepository;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import static org.apache.fineract.infrastructure.core.data.ApiParameterError.parameterError;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeRepositoryWrapper;
import org.apache.fineract.portfolio.fund.domain.Fund;
import org.apache.fineract.portfolio.fund.domain.FundRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChargeSplitService {

    private final ChargeSplitRepository splitRepository;
    private final ChargeRepositoryWrapper chargeRepository;
    private final FundRepository fundRepository;
    private final GLAccountRepository glAccountRepository;

    @Transactional
    public CommandProcessingResult createSplit(JsonCommand command) {
        try {
            // Validate and get charge
            Long chargeId = command.entityId();
            Charge charge = chargeRepository.findOneWithNotFoundDetection(chargeId);

            // Validate and get fund
            Long fundId = command.longValueOfParameterNamed("fundId");
            Fund fund = fundRepository.findById(fundId)
                    .orElseThrow(() -> new PlatformApiDataValidationException("error.msg.fund.not.found", 
                            "Fund with id " + fundId + " not found", 
                            List.of(parameterError("error.msg.fund.not.found", 
                                    "Fund with id " + fundId + " not found", "fundId", fundId))));

            // Validate and get GL account
            Long glAccountId = command.longValueOfParameterNamed("glAccountId");
            GLAccount glAccount = glAccountRepository.findById(glAccountId)
                    .orElseThrow(() -> new PlatformApiDataValidationException("error.msg.gl.account.not.found", 
                            "GL Account with id " + glAccountId + " not found", 
                            List.of(parameterError("error.msg.gl.account.not.found", 
                                    "GL Account with id " + glAccountId + " not found", "glAccountId", glAccountId))));

            // Check if split already exists
            if (splitRepository.findByChargeIdAndFundId(chargeId, fundId).isPresent()) {
                throw new PlatformApiDataValidationException("error.msg.split.already.exists", 
                        "Split already exists for charge " + chargeId + " and fund " + fundId,
                        List.of(parameterError("error.msg.split.already.exists", 
                                "Split already exists for charge " + chargeId + " and fund " + fundId, "chargeId", chargeId)));
            }

            // Create split
            ChargeSplit split = ChargeSplit.fromJson(command, charge, fund, glAccount);

            // Validate split totals
            validateSplitTotals(chargeId, split);

            // Save split
            ChargeSplit savedSplit = splitRepository.save(split);

            log.info("Created charge stakeholder split: {} for charge: {} and fund: {}", 
                    savedSplit.getId(), chargeId, fundId);

            return new CommandProcessingResultBuilder()
                    .withEntityId(savedSplit.getId())
                    .build();

        } catch (Exception e) {
            log.error("Error creating charge stakeholder split", e);
            throw e;
        }
    }

    @Transactional
    public CommandProcessingResult updateSplit(Long splitId, JsonCommand command) {
        try {
            // Get existing split
            ChargeSplit split = splitRepository.findById(splitId)
                    .orElseThrow(() -> new PlatformApiDataValidationException("error.msg.split.not.found", 
                            "Split with id " + splitId + " not found",
                            List.of(parameterError("error.msg.split.not.found", 
                                    "Split with id " + splitId + " not found", "splitId", splitId))));

            // Update split
            Map<String, Object> changes = split.update(command);

            // If fund is being updated, validate it exists
            if (changes.containsKey("fundId")) {
                Long fundId = command.longValueOfParameterNamed("fundId");
                Fund fund = fundRepository.findById(fundId)
                        .orElseThrow(() -> new PlatformApiDataValidationException("error.msg.fund.not.found", 
                                "Fund with id " + fundId + " not found",
                                List.of(parameterError("error.msg.fund.not.found", 
                                        "Fund with id " + fundId + " not found", "fundId", fundId))));
                split.setFund(fund);
            }

            // If GL account is being updated, validate it exists
            if (changes.containsKey("glAccountId")) {
                Long glAccountId = command.longValueOfParameterNamed("glAccountId");
                GLAccount glAccount = glAccountRepository.findById(glAccountId)
                        .orElseThrow(() -> new PlatformApiDataValidationException("error.msg.gl.account.not.found", 
                                "GL Account with id " + glAccountId + " not found",
                                List.of(parameterError("error.msg.gl.account.not.found", 
                                        "GL Account with id " + glAccountId + " not found", "glAccountId", glAccountId))));
                split.setGlAccount(glAccount);
            }

            // Validate split totals if split value or type changed
            if (changes.containsKey("splitValue") || changes.containsKey("splitType")) {
                validateSplitTotals(split.getCharge().getId(), split);
            }

            // Save updated split
            saveSplit(split);

            log.info("Updated charge stakeholder split: {}", splitId);

            return new CommandProcessingResultBuilder()
                    .withEntityId(splitId)
                    .with(changes)
                    .build();

        } catch (Exception e) {
            log.error("Error updating charge stakeholder split: {}", splitId, e);
            throw e;
        }
    }

    @Transactional
    public CommandProcessingResult deleteSplit(Long splitId) {
        try {
            // Get existing split
            ChargeSplit split = splitRepository.findById(splitId)
                    .orElseThrow(() -> new PlatformApiDataValidationException("error.msg.split.not.found", 
                            "Split with id " + splitId + " not found",
                            List.of(parameterError("error.msg.split.not.found", 
                                    "Split with id " + splitId + " not found", "splitId", splitId))));

            Long chargeId = split.getCharge().getId();

            // Delete split
            splitRepository.delete(split);

            log.info("Deleted charge stakeholder split: {} for charge: {}", splitId, chargeId);

            return new CommandProcessingResultBuilder()
                    .withEntityId(splitId)
                    .build();

        } catch (Exception e) {
            log.error("Error deleting charge stakeholder split: {}", splitId, e);
            throw e;
        }
    }

    public List<ChargeSplitData> getSplitsByChargeId(Long chargeId) {
        List<ChargeSplit> splits = splitRepository.findActiveSplitsByChargeId(chargeId);
        return splits.stream()
            .map(ChargeSplitData::fromEntity)
            .collect(Collectors.toList());
    }

    public List<ChargeSplitData> getSplitsByFundId(Long fundId) {
        List<ChargeSplit> splits = splitRepository.findByFundIdAndActive(fundId, true);
        return splits.stream()
            .map(ChargeSplitData::fromEntity)
            .collect(Collectors.toList());
    }

    /**
     * Save a charge split (for updates)
     */
    public ChargeSplit saveSplit(ChargeSplit split) {
        return splitRepository.save(split);
    }

    private void validateSplitTotals(Long chargeId, ChargeSplit newSplit) {
        List<ChargeSplit> existingSplits = splitRepository.findActiveSplitsByChargeId(chargeId);
        
        BigDecimal totalPercentage = BigDecimal.ZERO;
        BigDecimal totalFlatAmount = BigDecimal.ZERO;

        // Add existing splits
        for (ChargeSplit split : existingSplits) {
            if (split.getId() != null && !split.getId().equals(newSplit.getId())) { // Exclude the new/updated split
                if (split.isPercentageSplit()) {
                    totalPercentage = totalPercentage.add(split.getSplitValue());
                } else if (split.isFlatAmountSplit()) {
                    totalFlatAmount = totalFlatAmount.add(split.getSplitValue());
                }
            }
        }

        // Add new/updated split
        if (newSplit.isPercentageSplit()) {
            totalPercentage = totalPercentage.add(newSplit.getSplitValue());
        } else if (newSplit.isFlatAmountSplit()) {
            totalFlatAmount = totalFlatAmount.add(newSplit.getSplitValue());
        }

        // Validate totals
        if (totalPercentage.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new PlatformApiDataValidationException("error.msg.fee.split.total.percentage.exceeds.100", 
                    "Total percentage splits cannot exceed 100%",
                    List.of(parameterError("error.msg.fee.split.total.percentage.exceeds.100", 
                            "Total percentage splits cannot exceed 100%", "splitValue", totalPercentage)));
        }

        // Note: For flat amounts, we can't validate against total fee amount here since it's not available
        // This validation will be done at transaction time in FeeSplitService
    }
}
