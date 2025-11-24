package com.paystack.fineract.portfolio.account.handler;

import com.paystack.fineract.portfolio.account.service.PaystackSavingsAccountWritePlatformServiceJpaRepositoryImpl;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.annotation.CommandType;
import org.apache.fineract.commands.handler.NewCommandSourceHandler;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@CommandType(entity = "SAVINGSACCOUNTCHARGE", action = "REACTIVATE")
public class ReactivateSavingsAccountChargeCommandHandler implements NewCommandSourceHandler {

    private final PaystackSavingsAccountWritePlatformServiceJpaRepositoryImpl savingsAccountWritePlatformService;

    @Transactional
    @Override
    public CommandProcessingResult processCommand(JsonCommand command) {
        return savingsAccountWritePlatformService.reactivateSavingsAccountCharge(command.getSavingsId(), command.entityId(), command);
    }
}
