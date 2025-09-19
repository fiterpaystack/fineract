package com.paystack.fineract.portfolio.discount.data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data transfer object for discount rule type information
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiscountRuleTypeInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String ruleType;
    private String category;
    private String description;
    private List<String> requiredParameters;
    private List<String> optionalParameters;
    private Map<String, String> parameterDescriptions;
}
