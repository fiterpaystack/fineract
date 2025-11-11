package com.paystack.fineract.client.charge.handler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.paystack.fineract.client.charge.dto.ClientChargeOverrideRequest;
import com.paystack.fineract.client.charge.dto.ClientChargeOverrideResult;
import com.paystack.fineract.client.charge.dto.ClientChargeOverrideSlabRequest;
import com.paystack.fineract.client.charge.service.ExtendedClientChargeWritePlatformService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.annotation.CommandType;
import org.apache.fineract.commands.handler.NewCommandSourceHandler;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.springframework.stereotype.Service;

@Service
@CommandType(entity = "CLIENTCHARGEOVERRIDE", action = "CREATE")
@RequiredArgsConstructor
public class CreateClientChargeOverrideCommandHandler implements NewCommandSourceHandler {

    private final ExtendedClientChargeWritePlatformService writePlatformService;

    @Override
    public CommandProcessingResult processCommand(JsonCommand command) {
        ClientChargeOverrideRequest req = toRequest(command);
        ClientChargeOverrideResult result = writePlatformService.create(req);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(result.getId()).build();
    }

    private static ClientChargeOverrideRequest toRequest(JsonCommand command) {
        ClientChargeOverrideRequest r = new ClientChargeOverrideRequest();
        r.setClientId(command.getClientId());
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
        if (command.hasParameter("active")) {
            r.setActive(command.booleanObjectValueOfParameterNamed("active"));
        }
        if (command.hasParameter("slabs")) {
            r.setSlabs(extractSlabs(command.arrayOfParameterNamed("slabs")));
        }
        return r;
    }

    private static List<ClientChargeOverrideSlabRequest> extractSlabs(JsonArray array) {
        if (array == null || array.size() == 0) {
            return Collections.emptyList();
        }
        List<ClientChargeOverrideSlabRequest> slabs = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject obj = element.getAsJsonObject();
            slabs.add(ClientChargeOverrideSlabRequest.builder().id(getLong(obj, "id")).fromAmount(getBigDecimal(obj, "fromAmount"))
                    .toAmount(getBigDecimal(obj, "toAmount")).value(getBigDecimal(obj, "value")).build());
        }
        return slabs;
    }

    private static Long getLong(JsonObject obj, String name) {
        if (obj.has(name) && !obj.get(name).isJsonNull()) {
            return obj.get(name).getAsLong();
        }
        return null;
    }

    private static BigDecimal getBigDecimal(JsonObject obj, String name) {
        if (obj.has(name) && !obj.get(name).isJsonNull()) {
            return obj.get(name).getAsBigDecimal();
        }
        return null;
    }
}
