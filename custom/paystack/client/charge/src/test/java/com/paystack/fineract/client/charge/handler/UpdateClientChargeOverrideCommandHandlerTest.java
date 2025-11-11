package com.paystack.fineract.client.charge.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonElement;
import com.paystack.fineract.client.charge.dto.ClientChargeOverrideRequest;
import com.paystack.fineract.client.charge.dto.ClientChargeOverrideResult;
import com.paystack.fineract.client.charge.service.ExtendedClientChargeWritePlatformService;
import java.util.List;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockitoAnnotations;

class UpdateClientChargeOverrideCommandHandlerTest {

    private ExtendedClientChargeWritePlatformService writeService;
    private UpdateClientChargeOverrideCommandHandler handler;
    private final FromJsonHelper fromJsonHelper = new FromJsonHelper();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        writeService = mock(ExtendedClientChargeWritePlatformService.class);
        handler = new UpdateClientChargeOverrideCommandHandler(writeService);
    }

    @Test
    void shouldMapSlabsFromCommand() {
        String json = """
                {
                  "clientId": 55,
                  "chargeId": 15,
                  "active": false,
                  "slabs": [
                    {"fromAmount":0,"toAmount":5000,"value":10},
                    {"fromAmount":5000.01,"toAmount":null,"value":25}
                  ]
                }
                """;
        JsonElement parsed = fromJsonHelper.parse(json);
        JsonCommand command = JsonCommand.from(json, parsed, fromJsonHelper, "CLIENTCHARGEOVERRIDE", 1122L, null, null, 55L, null, null,
                null, "/v1/clients/55/charges/extended/1122", null, null, null, null, null);

        ClientChargeOverrideResult result = new ClientChargeOverrideResult();
        result.setId(1122L);
        when(writeService.update(eq(1122L), any())).thenReturn(result);

        handler.processCommand(command);

        ArgumentCaptor<ClientChargeOverrideRequest> captor = ArgumentCaptor.forClass(ClientChargeOverrideRequest.class);
        verify(writeService).update(eq(1122L), captor.capture());
        ClientChargeOverrideRequest request = captor.getValue();

        assertThat(request.getClientId()).isEqualTo(55L);
        assertThat(request.getChargeId()).isEqualTo(15L);
        assertThat(request.getActive()).isFalse();
        assertThat(request.getSlabs()).hasSize(2);
        assertThat(request.getSlabs().get(0).getFromAmount()).isEqualByComparingTo("0");
        assertThat(request.getSlabs().get(1).getToAmount()).isNull();
    }

    @Test
    void shouldHandleEmptySlabArray() {
        String json = """
                {
                  "clientId": 55,
                  "chargeId": 15,
                  "slabs": []
                }
                """;
        JsonElement parsed = fromJsonHelper.parse(json);
        JsonCommand command = JsonCommand.from(json, parsed, fromJsonHelper, "CLIENTCHARGEOVERRIDE", 99L, null, null, 55L, null, null, null,
                "/v1/clients/55/charges/extended/99", null, null, null, null, null);

        ClientChargeOverrideResult result = new ClientChargeOverrideResult();
        result.setId(99L);
        when(writeService.update(eq(99L), any())).thenReturn(result);

        handler.processCommand(command);

        ArgumentCaptor<ClientChargeOverrideRequest> captor = ArgumentCaptor.forClass(ClientChargeOverrideRequest.class);
        verify(writeService).update(eq(99L), captor.capture());
        ClientChargeOverrideRequest request = captor.getValue();

        assertThat(request.getSlabs()).isEqualTo(List.of());
    }
}
