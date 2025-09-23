package com.paystack.fineract.portfolio.client.handler;

import com.paystack.fineract.portfolio.client.service.PaystackClientWritePlatformService;
import org.apache.fineract.commands.annotation.CommandType;
import org.apache.fineract.commands.handler.NewCommandSourceHandler;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@CommandType(entity = "CLIENT", action = "UPGRADETOENTITY")
public class UpgradeClientToEntityCommandHandler implements NewCommandSourceHandler {

    private final PaystackClientWritePlatformService writePlatformService;

    @Autowired
    public UpgradeClientToEntityCommandHandler(final PaystackClientWritePlatformService writePlatformService) {
        this.writePlatformService = writePlatformService;
    }

    @Transactional
    @Override
    public CommandProcessingResult processCommand(final JsonCommand command) {
        return this.writePlatformService.upgradeClientToEntity(command.entityId(), command);
    }
}
