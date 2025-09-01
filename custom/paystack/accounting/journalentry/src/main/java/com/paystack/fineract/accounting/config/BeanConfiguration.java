package com.paystack.fineract.accounting.config;

import com.paystack.fineract.accounting.journalentry.PaystackAccrualBasedAccountingProcessorForSavings;
import com.paystack.fineract.accounting.journalentry.PaystackCashBasedAccountingProcessorForSavings;
import org.apache.fineract.accounting.journalentry.service.AccountingProcessorForSavings;
import org.apache.fineract.accounting.journalentry.service.AccountingProcessorHelper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class BeanConfiguration {

    @Bean
    @Primary
    AccountingProcessorForSavings cashBasedAccountingProcessorForSavings(AccountingProcessorHelper helper) {
        return new PaystackCashBasedAccountingProcessorForSavings(helper);
    }

    @Bean
    @Primary
    AccountingProcessorForSavings accrualBasedAccountingProcessorForSavings(AccountingProcessorHelper helper) {
        return new PaystackAccrualBasedAccountingProcessorForSavings(helper);
    }
}
