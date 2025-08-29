package com.paystack.fineract.portfolio.account.data;

import com.paystack.fineract.portfolio.account.domain.ChargeSplit;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class ChargeSplitData implements Serializable {

    private Long id;
    private Long chargeId;
    private String chargeName;
    private Long fundId;
    private String fundName;
    private String splitType;
    private BigDecimal splitValue;
    private Long glAccountId;
    private String glAccountName;
    private String glAccountCode;
    private String glAccountHierarchy;
    private Boolean active;
    private OffsetDateTime createdDate;
    private OffsetDateTime lastModifiedDate;
    private Long createdBy;
    private Long lastModifiedBy;

    public static ChargeSplitData fromEntity(ChargeSplit split) {
        return new ChargeSplitData().setId(split.getId()).setChargeId(split.getCharge().getId()).setChargeName(split.getCharge().getName())
                .setFundId(split.getFund().getId()).setFundName(split.getFund().getName()).setSplitType(split.getSplitType())
                .setSplitValue(split.getSplitValue()).setGlAccountId(split.getGlAccount().getId())
                .setGlAccountName(split.getGlAccount().getName()).setGlAccountCode(split.getGlAccount().getGlCode())
                .setGlAccountHierarchy(split.getGlAccount().getHierarchy()).setActive(split.isActive())
                .setCreatedDate(split.getCreatedDate().orElse(null)).setLastModifiedDate(split.getLastModifiedDate().orElse(null))
                .setCreatedBy(split.getCreatedBy().orElse(null)).setLastModifiedBy(split.getLastModifiedBy().orElse(null));
    }
}
