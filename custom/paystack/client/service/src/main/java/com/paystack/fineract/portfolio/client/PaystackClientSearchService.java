package com.paystack.fineract.portfolio.client;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.service.PagedRequest;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientEnumerations;
import org.apache.fineract.portfolio.client.domain.ClientRepository;
import org.apache.fineract.portfolio.client.service.search.ClientSearchService;
import org.apache.fineract.portfolio.client.service.search.domain.ClientSearchData;
import org.apache.fineract.portfolio.client.service.search.domain.ClientTextSearch;
import org.apache.fineract.portfolio.client.service.search.mapper.ClientSearchDataMapper;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@Primary
@Transactional(readOnly = true)
public class PaystackClientSearchService extends ClientSearchService {

    private final ClientRepository clientRepository;
    private final SavingsAccountRepository savingsAccountRepository;

    public PaystackClientSearchService(PlatformSecurityContext context, ClientRepository clientRepository,
            ClientSearchDataMapper clientSearchDataMapper, SavingsAccountRepository savingsAccountRepository) {
        super(context, clientRepository, clientSearchDataMapper);
        this.clientRepository = clientRepository;
        this.savingsAccountRepository = savingsAccountRepository;
    }

    @Override
    public Page<ClientSearchData> searchByText(PagedRequest<ClientTextSearch> searchRequest) {
        try {
            // Get the original search results from parent
            Page<ClientSearchData> originalResults = super.searchByText(searchRequest);

            // Check if we should enhance with savings account search
            String searchText = searchRequest.getRequest().map(ClientTextSearch::getText).orElse("");

            if (StringUtils.hasText(searchText) && searchText.matches(".*\\d.*")) {
                // If parent search returned 0 results, try savings account search
                if (originalResults.getTotalElements() == 0) {
                    Page<ClientSearchData> enhancedResults = searchBySavingsAccount(searchText, searchRequest.toPageable());
                    return enhancedResults;
                }
            }

            return originalResults;
        } catch (Exception e) {
            log.error("Error in enhanced client search, falling back to parent implementation", e);
            return super.searchByText(searchRequest);
        }
    }

    private Page<ClientSearchData> searchBySavingsAccount(String searchText, Pageable pageable) {
        try {
            // Use existing savings account lookup
            SavingsAccount savingsAccount = savingsAccountRepository.findSavingsAccountByAccountNumber(searchText);

            if (savingsAccount == null) {
                return Page.empty(pageable);
            }

            Client client = savingsAccount.getClient();
            if (client == null) {
                return Page.empty(pageable);
            }

            // Create single result page
            ClientSearchData data = new ClientSearchData();
            data.setId(client.getId());
            data.setDisplayName(client.getDisplayName());
            data.setExternalId(client.getExternalId());
            data.setAccountNumber(client.getAccountNumber());
            data.setOfficeId(client.getOffice().getId());
            data.setOfficeName(client.getOffice().getName());
            data.setMobileNo(client.getMobileNo());
            data.setStatus(ClientEnumerations.status(client.getStatus()));
            data.setActivationDate(client.getActivationDate());
            data.setCreatedDate(client.getCreatedDate().orElse(null));

            return PageableExecutionUtils.getPage(List.of(data), pageable, () -> 1L);

        } catch (Exception e) {
            log.error("Error in savings account search", e);
            throw e;
        }
    }
}
