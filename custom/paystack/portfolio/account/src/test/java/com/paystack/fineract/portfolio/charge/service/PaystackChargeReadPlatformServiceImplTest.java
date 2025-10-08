package com.paystack.fineract.portfolio.charge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.paystack.fineract.portfolio.discount.data.DiscountRuleData;
import com.paystack.fineract.portfolio.discount.domain.DiscountRule;
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
        DiscountRule mockRule = new DiscountRule();
        mockRule.setId(1L);
        mockRule.setName("Test Rule");
        mockRule.setRuleType("TIME_BASED");
        mockRule.setActive(true);
        mockRule.setRulePriority(1);

        DiscountRuleData mockRuleData = new DiscountRuleData();
        mockRuleData.setId(1L);
        mockRuleData.setName("Test Rule");
        mockRuleData.setRuleType("TIME_BASED");
        mockRuleData.setActive(true);
        mockRuleData.setRulePriority(1);

        when(discountRuleService.getAssignedDiscountRules(eq("CHARGE"), eq(chargeId))).thenReturn(List.of(mockRule));
        when(discountRuleService.mapToData(mockRule)).thenReturn(mockRuleData);

        // When
        Map<String, Object> additionalAttributes = service.getAdditionalAttributes(chargeId);

        // Then
        assertThat(additionalAttributes).isNotNull();
        assertThat(additionalAttributes.get("enableDiscountEngine")).isEqualTo(true);
        assertThat(additionalAttributes.get("discountRules")).isInstanceOf(List.class);

        @SuppressWarnings("unchecked")
        List<DiscountRuleData> discountRules = (List<DiscountRuleData>) additionalAttributes.get("discountRules");
        assertThat(discountRules).hasSize(1);
        assertThat(discountRules.get(0).getName()).isEqualTo("Test Rule");
    }

    @Test
    void shouldHandleEmptyDiscountRules() {
        // Given
        Long chargeId = 1L;
        when(discountRuleService.getAssignedDiscountRules(eq("CHARGE"), eq(chargeId))).thenReturn(List.of());

        // When
        Map<String, Object> additionalAttributes = service.getAdditionalAttributes(chargeId);

        // Then
        assertThat(additionalAttributes).isNotNull();
        assertThat(additionalAttributes.get("enableDiscountEngine")).isEqualTo(false);
        assertThat(additionalAttributes.get("discountRules")).isInstanceOf(List.class);

        @SuppressWarnings("unchecked")
        List<DiscountRuleData> discountRules = (List<DiscountRuleData>) additionalAttributes.get("discountRules");
        assertThat(discountRules).isEmpty();
    }

    @Test
    void shouldHandleDiscountServiceException() {
        // Given
        Long chargeId = 1L;
        when(discountRuleService.getAssignedDiscountRules(eq("CHARGE"), eq(chargeId)))
                .thenThrow(new RuntimeException("Service unavailable"));

        // When
        Map<String, Object> additionalAttributes = service.getAdditionalAttributes(chargeId);

        // Then
        assertThat(additionalAttributes).isNotNull();
        assertThat(additionalAttributes.get("enableDiscountEngine")).isEqualTo(false);
        assertThat(additionalAttributes.get("discountRules")).isInstanceOf(List.class);

        @SuppressWarnings("unchecked")
        List<DiscountRuleData> discountRules = (List<DiscountRuleData>) additionalAttributes.get("discountRules");
        assertThat(discountRules).isEmpty();
    }
}
