package com.paystack.fineract.client.charge.handler;

import com.paystack.fineract.client.charge.service.ExtendedClientChargeWritePlatformService;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.annotation.CommandType;
import org.apache.fineract.commands.handler.NewCommandSourceHandler;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.springframework.stereotype.Service;

@Service
@CommandType(entity = "CLIENTCHARGEOVERRIDE", action = "DELETE")
@RequiredArgsConstructor
public class DeleteClientChargeOverrideCommandHandler implements NewCommandSourceHandler {

    private final ExtendedClientChargeWritePlatformService writeService;

    @Override
    public CommandProcessingResult processCommand(JsonCommand command) {
        Long id = command.entityId();
        writeService.delete(id);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(id).build();
    }
}
