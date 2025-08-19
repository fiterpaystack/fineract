package com.paystack.fineract.client.charge.handler;

import com.paystack.fineract.client.charge.dto.ClientChargeOverrideRequest;
import com.paystack.fineract.client.charge.dto.ClientChargeOverrideResult;
import com.paystack.fineract.client.charge.service.ExtendedClientChargeWritePlatformService;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.annotation.CommandType;
import org.apache.fineract.commands.handler.NewCommandSourceHandler;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.springframework.stereotype.Service;

@Service
@CommandType(entity = "CLIENTCHARGEOVERRIDE", action = "UPDATE")
@RequiredArgsConstructor
public class UpdateClientChargeOverrideCommandHandler implements NewCommandSourceHandler {

    private final ExtendedClientChargeWritePlatformService writeService;

    @Override
    public CommandProcessingResult processCommand(JsonCommand command) {
        Long id = command.entityId();
        ClientChargeOverrideRequest req = toRequest(command);
        ClientChargeOverrideResult result = writeService.update(id, req);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(result.getId()).build();
    }

    private static ClientChargeOverrideRequest toRequest(JsonCommand command) {
        ClientChargeOverrideRequest r = new ClientChargeOverrideRequest();
        if (command.hasParameter("clientId")) {
            r.setClientId(command.longValueOfParameterNamed("clientId"));
        }
        if (command.hasParameter("chargeId")) {
            r.setChargeId(command.longValueOfParameterNamed("chargeId"));
        }
        if (command.hasParameter("amount")) {
            r.setAmount(command.bigDecimalValueOfParameterNamed("amount"));
        }
        if (command.hasParameter("minCap")) {
            r.setMinCap(command.bigDecimalValueOfParameterNamed("minCap"));
        }
        if (command.hasParameter("maxCap")) {
            r.setMaxCap(command.bigDecimalValueOfParameterNamed("maxCap"));
        }
        return r;
    }
}
