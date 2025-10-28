package com.paystack.fineract.portfolio.account.domain;

import java.time.LocalDate;
import java.util.Set;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.charge.domain.ChargeRepositoryWrapper;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountCharge;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountChargeAssembler;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountChargeRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsProduct;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class PaystackSavingsAccountChargeAssembler extends SavingsAccountChargeAssembler {

    public PaystackSavingsAccountChargeAssembler(FromJsonHelper fromApiJsonHelper, ChargeRepositoryWrapper chargeRepository,
            SavingsAccountChargeRepository savingsAccountChargeRepository) {
        super(fromApiJsonHelper, chargeRepository, savingsAccountChargeRepository);
    }

    @Override
    public Set<SavingsAccountCharge> fromSavingsProduct(final SavingsProduct savingsProduct) {
        // Reuse superclass logic for all non-specified-due-date charges
        final Set<SavingsAccountCharge> savingsAccountCharges = super.fromSavingsProduct(savingsProduct);

        // Add only specified-due-date charges with today's date (unique behavior for Paystack)
        for (Charge charge : savingsProduct.charges()) {
            ChargeTimeType chargeTime = null;
            if (charge.getChargeTimeType() != null) {
                chargeTime = ChargeTimeType.fromInt(charge.getChargeTimeType());
            }
            if (chargeTime == null || !chargeTime.isOnSpecifiedDueDate()) {
                continue; // already handled by super
            }

            ChargeCalculationType chargeCalculation = null;
            if (charge.getChargeCalculation() != null) {
                chargeCalculation = ChargeCalculationType.fromInt(charge.getChargeCalculation());
            }

            final boolean status = true;
            final LocalDate dueDate = DateUtils.getLocalDateOfTenant(); // default to today's date
            final SavingsAccountCharge savingsAccountCharge = SavingsAccountCharge.createNewWithoutSavingsAccount(charge,
                    charge.getAmount(), chargeTime, chargeCalculation, dueDate, status, charge.getFeeOnMonthDay(), charge.feeInterval());
            savingsAccountCharges.add(savingsAccountCharge);
        }
        return savingsAccountCharges;
    }
}
