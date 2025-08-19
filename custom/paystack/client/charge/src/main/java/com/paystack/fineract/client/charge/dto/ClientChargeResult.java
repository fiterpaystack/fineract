package com.paystack.fineract.client.charge.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;
import org.apache.fineract.portfolio.charge.data.ChargeData;

@Getter
@Builder(toBuilder = true)
public class ClientChargeResult {

    private Long clientId;
    private Long overrideId;
    private BigDecimal overrideMinCap;
    private BigDecimal overrideMaxCap;
    private BigDecimal overrideAmount;
    private Boolean overrideActive;
    @JsonUnwrapped
    private ChargeData chargeData;
}
