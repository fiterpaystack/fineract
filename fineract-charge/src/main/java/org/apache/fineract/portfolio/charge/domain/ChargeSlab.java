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

package org.apache.fineract.portfolio.charge.domain;

import static org.apache.fineract.portfolio.charge.api.ChargesApiConstants.fromAmountParamName;
import static org.apache.fineract.portfolio.charge.api.ChargesApiConstants.toAmountParamName;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

@Entity
@Table(name = "m_fee_charge_slab")
@Getter
@RequiredArgsConstructor
@AllArgsConstructor
public class ChargeSlab extends AbstractPersistableCustom<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "charge_id")
    private Charge charge;

    @Column(name = "fee_amount")
    private BigDecimal value;

    @Column(name = "from_amount")
    private BigDecimal fromAmount;

    @Column(name = "to_amount")
    private BigDecimal toAmount;

    public static List<ChargeSlab> assembleFrom(JsonCommand command, Charge charge) {

        final List chartList = new ArrayList<>();

        JsonArray array = command.jsonElement("chart").getAsJsonObject().getAsJsonArray("chartSlabs");
        for (JsonElement jsonElement : array) {
            JsonObject obj = jsonElement.getAsJsonObject();

            final BigDecimal fromAmount = obj.get("fromAmount").getAsBigDecimal();
            BigDecimal toAmount = null;

            if (obj.has("toAmount")) {
                toAmount = obj.get("toAmount").getAsBigDecimal();
            }

            final BigDecimal value = obj.get("value").getAsBigDecimal();
            chartList.add(new ChargeSlab(charge, value, fromAmount, toAmount));
        }

        return chartList;
    }

    public static ChargeSlab assembleFrom(JsonCommand command, Charge charge, Locale locale) {

        BigDecimal fromAmount = null;
        BigDecimal toAmount = null;

        if (command.parameterExists("fromAmount")) {
            fromAmount = command.bigDecimalValueOfParameterNamed("fromAmount", locale);
        }

        if (command.parameterExists("toAmount")) {
            toAmount = command.bigDecimalValueOfParameterNamed("toAmount", locale);
        }

        final BigDecimal value = command.bigDecimalValueOfParameterNamed("value", locale);
        return new ChargeSlab(charge, value, fromAmount, toAmount);

    }

    public void update(final JsonCommand command, final Map<String, Object> actualChanges, final DataValidatorBuilder baseDataValidator,
            final Locale locale) {

        if (command.isChangeInBigDecimalParameterNamed(fromAmountParamName, this.fromAmount, locale)) {
            final BigDecimal newValue = command.bigDecimalValueOfParameterNamed(fromAmountParamName, locale);
            actualChanges.put(fromAmountParamName, newValue);
            this.fromAmount = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed(toAmountParamName, this.toAmount, locale)) {
            final BigDecimal newValue = command.bigDecimalValueOfParameterNamed(toAmountParamName, locale);
            actualChanges.put(toAmountParamName, newValue);
            this.toAmount = newValue;
        }

        if (command.isChangeInBigDecimalParameterNamed("value", this.value, locale)) {
            final BigDecimal newValue = command.bigDecimalValueOfParameterNamed("value", locale);
            actualChanges.put("value", newValue);
            this.value = newValue;
        }

        validateChargeSlabPlatformRules(command, baseDataValidator, locale);
    }

    public void validateChargeSlabPlatformRules(final JsonCommand chartSlabsCommand, final DataValidatorBuilder baseDataValidator,
            Locale locale) {
        if (isfromAmountGreaterThantoAmount()) {
            final Integer fromAmount = chartSlabsCommand.integerValueOfParameterNamed(fromAmountParamName, locale);
            baseDataValidator.parameter(fromAmountParamName).value(fromAmount).failWithCode("from.period.is.greater.than.to.period");
        }
    }

    public boolean isfromAmountGreaterThantoAmount() {
        boolean isGreater = false;
        if (this.toAmount != null && this.fromAmount.compareTo(this.toAmount) > 0) {
            isGreater = true;
        }
        return isGreater;
    }

    public boolean isValueSame(final ChargeSlab that) {
        return isBigDecimalSame(this.value, that.value);
    }

    public boolean isPeriodsSame(final ChargeSlab that) {
        return isBigDecimalSame(this.fromAmount, that.fromAmount) && isBigDecimalSame(this.toAmount, that.toAmount);
    }

    public boolean isIntegerSame(final Integer obj1, final Integer obj2) {
        if (obj1 == null || obj2 == null) {
            if (Objects.equals(obj1, obj2)) {
                return true;
            }
            return false;
        }
        return obj1.equals(obj2);
    }

    public boolean isBigDecimalSame(final BigDecimal obj1, final BigDecimal obj2) {
        if (obj1 == null || obj2 == null) {
            if (Objects.compare(obj1, obj2, Comparator.nullsFirst(Comparator.naturalOrder())) == 0 ? Boolean.TRUE : Boolean.FALSE) {
                return true;
            }
            return false;
        }
        return obj1.compareTo(obj2) == 0;
    }

    public boolean isValidChart() {
        return this.fromAmount != null;
    }

    public boolean isRateChartOverlapping(final ChargeSlab that) {
        boolean isPeriodOverLapping = isPeriodOverlapping(that);
        boolean isPeriodSame = isPeriodsSame(that);
        return (isPeriodOverLapping && !isPeriodSame);

    }

    private boolean isPeriodOverlapping(final ChargeSlab that) {
        if (isBigDecimalSame(that.toAmount, this.toAmount)) {
            return true;
        } else if (isBigDecimalSame(that.fromAmount, this.fromAmount)) {
            return true;
        } else if (this.toAmount == null) {
            return true;
        } else if (that.toAmount == null) {
            if (that.fromAmount == null) {
                return true;
            }
            return that.fromAmount.compareTo(this.fromAmount) < 0;
        }
        return this.fromAmount.compareTo(that.toAmount) <= 0 && that.fromAmount.compareTo(this.toAmount) <= 0;
    }

    public boolean isRateChartHasGap(final ChargeSlab that) {
        if (isPeriodsSame(that)) {
            return false;
        } else {
            return isNotProperPeriodStart(that.fromAmount);
        }
    }

    private boolean isNotProperPeriodStart(final BigDecimal period) {
        return this.toAmount == null || (period != null && period.compareTo(this.toAmount.add(BigDecimal.ONE)) != 0);
    }

    public boolean isNotProperPriodEnd() {
        return !(this.toAmount == null);

    }
}
