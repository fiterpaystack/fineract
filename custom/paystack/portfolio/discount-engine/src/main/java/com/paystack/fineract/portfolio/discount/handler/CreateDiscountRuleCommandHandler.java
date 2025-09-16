package com.paystack.fineract.portfolio.discount.handler;

import com.paystack.fineract.portfolio.discount.service.DiscountRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.commands.annotation.CommandType;
import org.apache.fineract.commands.handler.NewCommandSourceHandler;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Command handler for creating discount rules
 */
@Service
@CommandType(entity = "DISCOUNTRULE", action = "CREATE")
@RequiredArgsConstructor
@Slf4j
public class CreateDiscountRuleCommandHandler implements NewCommandSourceHandler {

    private final DiscountRuleService discountRuleService;

    @Override
    @Transactional
    public CommandProcessingResult processCommand(JsonCommand command) {
        return discountRuleService.createDiscountRule(command);
    }
}
