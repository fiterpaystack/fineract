package com.paystack.fineract.portfolio.savings.data;

public final class PaystackSavingsProductAdditionalAttributes {

    private PaystackSavingsProductAdditionalAttributes() {
        // private constructor to prevent instantiation
    }

    public static final String EMT_LEVY_APPLICABLE = "isEmtLevyApplicable";
    public static final String EMT_LEVY_AMOUNT = "emtLevyAmount";
    public static final String EMT_LEVY_THRESHOLD = "emtLevyThreshold";
    public static final String OVERRIDE_GLOBAL_EMT_LEVY = "overrideGlobalEmtLevySetting";
}
