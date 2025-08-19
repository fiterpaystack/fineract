package com.paystack.fineract.client.charge.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.Builder;
import lombok.Getter;
import org.apache.fineract.portfolio.charge.data.ChargeData;

@Getter
@Builder(toBuilder = true)
public class ClientChargeResult {

    private Long chargeOverrideId;
    private Long clientId;
    private Long chargeId;
    @JsonUnwrapped
    private ChargeData chargeData;
}
