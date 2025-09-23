package com.paystack.fineract.portfolio.client.service;

import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.portfolio.client.service.ClientWritePlatformService;

public interface PaystackClientWritePlatformService extends ClientWritePlatformService {

    CommandProcessingResult upgradeClientToEntity(Long clientId, JsonCommand command);
}
