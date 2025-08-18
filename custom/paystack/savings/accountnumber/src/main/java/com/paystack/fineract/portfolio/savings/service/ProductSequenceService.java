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
package com.paystack.fineract.portfolio.savings.service;

import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.configuration.api.GlobalConfigurationConstants;
import org.apache.fineract.infrastructure.configuration.data.GlobalConfigurationPropertyData;
import org.apache.fineract.infrastructure.configuration.service.ConfigurationReadPlatformService;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.portfolio.savings.domain.SavingsProduct;
import org.apache.fineract.portfolio.savings.domain.SavingsProductRepository;
import org.springframework.stereotype.Service;
import com.paystack.fineract.portfolio.savings.domain.ProductAccountSequenceRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.apache.fineract.infrastructure.configuration.api.GlobalConfigurationConstants.CBN_INSTITUTION_CODE;
import static org.apache.fineract.portfolio.savings.SavingsApiConstants.SAVINGS_ACCOUNT_RESOURCE_NAME;
import static org.apache.fineract.portfolio.savings.SavingsApiConstants.SAVINGS_PRODUCT_RESOURCE_NAME;

@Service
@RequiredArgsConstructor
public class ProductSequenceService {

    private final ProductAccountSequenceRepository sequenceRepository;
    private final SavingsProductRepository savingsProductRepository;
    private final ConfigurationReadPlatformService configurationReadPlatformService;

    /**
     * Get the next sequence number for this savings product.
     */
    public long nextSavingsSeq(Long productId) {
        return sequenceRepository.nextForSavingsProduct(productId);
    }

    /**
     * Build the account number from product prefix + padded sequence.
     * Ensures final account number = 10 chars.
     */
    public String buildNubanSavingsAccountNo(Long productId, long seq) {
        final GlobalConfigurationPropertyData cbnConfig = this.configurationReadPlatformService
                .retrieveGlobalConfiguration(GlobalConfigurationConstants.CBN_INSTITUTION_CODE);
        String institutionCode = null; // "50547"
        if (cbnConfig.isEnabled()) {
            if (cbnConfig.getValue() != null) {
                institutionCode = cbnConfig.getValue().toString();
            }
        }
        if (institutionCode == null || institutionCode.length() != 5) {
            throwValidationForActiveStatus("validation.msg.savingsproduct.number.cbn.institution.code.not.set", "CBN Institution Code not available", "institution code");
        }
        // 2. Prepend 9 to make it 6 digits
        String fullInstitutionCode = "9" + institutionCode; // "950547"
        String prefix = savingsProductRepository.findById(productId)
                .map(SavingsProduct::getAccountNumberPrefix)
                .orElseThrow(() -> {
                    throwValidationForActiveStatus("validation.msg.savingsproduct.number.prefix.not.set", "Account number prefix not set for product", "accountnumberprefix");
                    return null; // Unreachable, required for lambda
                });

        if (prefix == null || prefix.length() != 2) {
            throwValidationForActiveStatus("validation.msg.savingsproduct.number.prefix.not.set", "Account number prefix not set for product", "accountnumberprefix");
        }

        // Convert sequence to string, pad with leading zeros to ensure at least 1 digit and total length 9
        String seqStr = String.valueOf(seq);
        seqStr = String.format("%0" + (9 - prefix.length()) + "d", seq);

        String nineDigitSerial = prefix + seqStr;   // "000000001" style

        // 4. Combine for check digit calculation
        String nubanBase = fullInstitutionCode + nineDigitSerial; // 6 + 9 = 15 digits

        // 5. Calculate check digit using your custom weighted sum
        int checkDigit = calculateCustomNubanCheckDigit(nubanBase);

        // 6. Final NUBAN account number: 9-digit serial + 1-digit check digit
        return nineDigitSerial + checkDigit;
    }

    private int calculateCustomNubanCheckDigit(String nubanBase) {
        int[] weights = {3,7,3,3,7,3,3,7,3,3,7,3,3,7,3};
        if (nubanBase.length() != weights.length) {
            throwValidationForActiveStatus("validation.msg.savingsproduct.number.invalid.length", "NUBAN base length is invalid, expected " + weights.length + " digits", "nubanBase");
        }

        int sum = 0;
        for (int i = 0; i < nubanBase.length(); i++) {
            int digit = Character.getNumericValue(nubanBase.charAt(i));
            sum += digit * weights[i];
        }

        int mod10 = sum % 10;
        return (10 - mod10) % 10;
    }

    private void throwValidationForActiveStatus(final String errorCode, String errorMessage, String parameter) {
        ApiParameterError error = ApiParameterError.parameterError(errorCode, errorMessage, parameter);
        throw new PlatformApiDataValidationException(Collections.singletonList(error));
    }

}
