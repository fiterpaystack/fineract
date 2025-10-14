package com.paystack.fineract.portfolio.charge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.paystack.fineract.portfolio.discount.service.DiscountRuleService;
import java.util.List;
import java.util.Map;
import org.apache.fineract.accounting.common.AccountingDropdownReadPlatformService;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainServiceJpa;
import org.apache.fineract.infrastructure.entityaccess.service.FineractEntityAccessUtil;
import org.apache.fineract.organisation.monetary.service.CurrencyReadPlatformService;
import org.apache.fineract.portfolio.charge.service.ChargeDropdownReadPlatformService;
import org.apache.fineract.portfolio.common.service.DropdownReadPlatformService;
import org.apache.fineract.portfolio.tax.service.TaxReadPlatformService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class PaystackChargeReadPlatformServiceImplTest {

    @Mock
    private CurrencyReadPlatformService currencyReadPlatformService;
    @Mock
    private ChargeDropdownReadPlatformService chargeDropdownReadPlatformService;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private DropdownReadPlatformService dropdownReadPlatformService;
    @Mock
    private FineractEntityAccessUtil fineractEntityAccessUtil;
    @Mock
    private AccountingDropdownReadPlatformService accountingDropdownReadPlatformService;
    @Mock
    private TaxReadPlatformService taxReadPlatformService;
    @Mock
    private ConfigurationDomainServiceJpa configurationDomainServiceJpa;
    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    @Mock
    private DiscountRuleService discountRuleService;

    private PaystackChargeReadPlatformServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PaystackChargeReadPlatformServiceImpl(currencyReadPlatformService, chargeDropdownReadPlatformService, jdbcTemplate,
                dropdownReadPlatformService, fineractEntityAccessUtil, accountingDropdownReadPlatformService, taxReadPlatformService,
                configurationDomainServiceJpa, namedParameterJdbcTemplate, discountRuleService);
    }

    @Test
    void shouldIncludeDiscountRulesInAdditionalAttributes() {
        // Given
        Long chargeId = 1L;
        com.paystack.fineract.portfolio.discount.data.DiscountRuleAssignmentData assignment = com.paystack.fineract.portfolio.discount.data.DiscountRuleAssignmentData
                .builder().ruleId(1L).ruleName("Test Rule").ruleType("TIME_BASED").active(true).rulePriority(1).assignmentPriority(1)
                .build();
        when(discountRuleService.getAssignmentDataForCharge(eq(chargeId))).thenReturn(List.of(assignment));

        // When
        Map<String, Object> additionalAttributes = service.getAdditionalAttributes(chargeId);

        // Then
        assertThat(additionalAttributes).isNotNull();
        assertThat(additionalAttributes.get("enableDiscountEngine")).isEqualTo(true);
        assertThat(additionalAttributes.get("discountRules")).isInstanceOf(List.class);

        @SuppressWarnings("unchecked")
        List<com.paystack.fineract.portfolio.discount.data.DiscountRuleAssignmentData> discountRules = (List<com.paystack.fineract.portfolio.discount.data.DiscountRuleAssignmentData>) additionalAttributes
                .get("discountRules");
        assertThat(discountRules).hasSize(1);
        assertThat(discountRules.get(0).getRuleName()).isEqualTo("Test Rule");
    }

    @Test
    void shouldHandleEmptyDiscountRules() {
        // Given
        Long chargeId = 1L;
        when(discountRuleService.getAssignmentDataForCharge(eq(chargeId))).thenReturn(List.of());

        // When
        Map<String, Object> additionalAttributes = service.getAdditionalAttributes(chargeId);

        // Then
        assertThat(additionalAttributes).isNotNull();
        assertThat(additionalAttributes.get("enableDiscountEngine")).isEqualTo(false);
        assertThat(additionalAttributes.get("discountRules")).isInstanceOf(List.class);

        @SuppressWarnings("unchecked")
        List<com.paystack.fineract.portfolio.discount.data.DiscountRuleAssignmentData> discountRules = (List<com.paystack.fineract.portfolio.discount.data.DiscountRuleAssignmentData>) additionalAttributes
                .get("discountRules");
        assertThat(discountRules).isEmpty();
    }

    @Test
    void shouldHandleDiscountServiceException() {
        // Given
        Long chargeId = 1L;
        when(discountRuleService.getAssignmentDataForCharge(eq(chargeId))).thenThrow(new RuntimeException("Service unavailable"));

        // When
        Map<String, Object> additionalAttributes = service.getAdditionalAttributes(chargeId);

        // Then
        assertThat(additionalAttributes).isNotNull();
        assertThat(additionalAttributes.get("enableDiscountEngine")).isEqualTo(false);
        assertThat(additionalAttributes.get("discountRules")).isInstanceOf(List.class);

        @SuppressWarnings("unchecked")
        List<com.paystack.fineract.portfolio.discount.data.DiscountRuleAssignmentData> discountRules = (List<com.paystack.fineract.portfolio.discount.data.DiscountRuleAssignmentData>) additionalAttributes
                .get("discountRules");
        assertThat(discountRules).isEmpty();
    }
}
