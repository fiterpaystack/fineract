package com.paystack.fineract.portfolio.account.service;

/**
 * Context to store transfer information for intra-client transfer detection. Used to pass client IDs between account
 * transfer operations and transaction processing to determine if a transfer is intra-client (same client) or
 * inter-client (different clients).
 */
public class TransferContext {

    private final Long fromClientId;
    private final Long toClientId;

    TransferContext(Long fromClientId, Long toClientId) {
        this.fromClientId = fromClientId;
        this.toClientId = toClientId;
    }

    Long getFromClientId() {
        return fromClientId;
    }

    Long getToClientId() {
        return toClientId;
    }
}
