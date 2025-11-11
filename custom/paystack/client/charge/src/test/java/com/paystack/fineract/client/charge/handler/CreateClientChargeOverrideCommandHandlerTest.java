package com.paystack.fineract.client.charge.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonElement;
import com.paystack.fineract.client.charge.dto.ClientChargeOverrideRequest;
import com.paystack.fineract.client.charge.dto.ClientChargeOverrideResult;
import com.paystack.fineract.client.charge.service.ExtendedClientChargeWritePlatformService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockitoAnnotations;

class CreateClientChargeOverrideCommandHandlerTest {

    private ExtendedClientChargeWritePlatformService writeService;
    private CreateClientChargeOverrideCommandHandler handler;
    private final FromJsonHelper fromJsonHelper = new FromJsonHelper();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        writeService = mock(ExtendedClientChargeWritePlatformService.class);
        handler = new CreateClientChargeOverrideCommandHandler(writeService);
    }

    @Test
    void shouldMapSlabsFromCommand() {
        String json = """
                {
                  "clientId": 55,
                  "chargeId": 15,
                  "active": true,
                  "slabs": [
                    {"id":3,"fromAmount":0,"toAmount":5000,"value":10},
                    {"id":1,"fromAmount":5001,"toAmount":50000,"value":25},
                    {"id":2,"fromAmount":50001,"toAmount":null,"value":500}
                  ]
                }
                """;
        JsonElement parsed = fromJsonHelper.parse(json);
        JsonCommand command = JsonCommand.from(json, parsed, fromJsonHelper, "CLIENTCHARGEOVERRIDE", null, null, null, 55L, null, null,
                null, "/v1/clients/55/charges/extended", null, null, null, null, null);

        ClientChargeOverrideResult result = new ClientChargeOverrideResult();
        result.setId(200L);
        when(writeService.create(any())).thenReturn(result);

        handler.processCommand(command);

        ArgumentCaptor<ClientChargeOverrideRequest> captor = ArgumentCaptor.forClass(ClientChargeOverrideRequest.class);
        verify(writeService).create(captor.capture());

        ClientChargeOverrideRequest request = captor.getValue();
        assertThat(request.getClientId()).isEqualTo(55L);
        assertThat(request.getChargeId()).isEqualTo(15L);
        assertThat(request.getActive()).isTrue();
        assertThat(request.getSlabs()).hasSize(3);

        assertThat(request.getSlabs().get(0).getId()).isEqualTo(3L);
        assertThat(request.getSlabs().get(0).getFromAmount()).isEqualByComparingTo("0");
        assertThat(request.getSlabs().get(0).getToAmount()).isEqualByComparingTo("5000");
        assertThat(request.getSlabs().get(0).getValue()).isEqualByComparingTo("10");

        assertThat(request.getSlabs().get(2).getId()).isEqualTo(2L);
        assertThat(request.getSlabs().get(2).getToAmount()).isNull();
        assertThat(request.getSlabs().get(2).getValue()).isEqualByComparingTo("500");
    }
}
