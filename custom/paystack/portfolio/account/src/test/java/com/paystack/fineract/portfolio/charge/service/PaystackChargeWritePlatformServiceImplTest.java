package com.paystack.fineract.portfolio.charge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.fineract.accounting.glaccount.domain.GLAccountRepositoryWrapper;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.entityaccess.service.FineractEntityAccessUtil;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeRepository;
import org.apache.fineract.portfolio.charge.domain.ChargeSlabRepository;
import org.apache.fineract.portfolio.charge.serialization.ChargeDefinitionCommandFromApiJsonDeserializer;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductRepository;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentTypeRepositoryWrapper;
import org.apache.fineract.portfolio.tax.domain.TaxGroup;
import org.apache.fineract.portfolio.tax.domain.TaxGroupRepositoryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class PaystackChargeWritePlatformServiceImplTest {

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private ChargeDefinitionCommandFromApiJsonDeserializer deserializer;
    @Mock
    private ChargeRepository chargeRepository;
    @Mock
    private LoanProductRepository loanProductRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private FineractEntityAccessUtil fineractEntityAccessUtil;
    @Mock
    private GLAccountRepositoryWrapper glAccountRepository;
    @Mock
    private TaxGroupRepositoryWrapper taxGroupRepository;
    @Mock
    private PaymentTypeRepositoryWrapper paymentTypeRepository;
    @Mock
    private ChargeSlabRepository chargeSlabRepository;
    @Mock
    private com.paystack.fineract.portfolio.discount.service.DiscountRuleService discountRuleService;

    private PaystackChargeWritePlatformServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PaystackChargeWritePlatformServiceImpl(context, deserializer, chargeRepository, loanProductRepository, jdbcTemplate,
                fineractEntityAccessUtil, glAccountRepository, taxGroupRepository, paymentTypeRepository, chargeSlabRepository,
                discountRuleService);
    }

    @Test
    void updateCharge_changesTaxGroup_whenAllowedAndNotUsed() {
        // Arrange
        JsonCommand command = Mockito.mock(JsonCommand.class);
        when(command.parameterExists("taxGroupId")).thenReturn(true);
        when(command.stringValueOfParameterNamed("taxGroupId")).thenReturn("5");
        when(command.json()).thenReturn("{}");
        when(command.commandId()).thenReturn(1L);

        Charge charge = Mockito.mock(Charge.class);
        TaxGroup existingGroup = Mockito.mock(TaxGroup.class);
        when(existingGroup.getId()).thenReturn(3L);
        when(charge.getTaxGroup()).thenReturn(existingGroup);
        when(chargeRepository.findById(10L)).thenReturn(Optional.of(charge));
        when(chargeRepository.save(any(Charge.class))).thenReturn(charge);

        TaxGroup newGroup = Mockito.mock(TaxGroup.class);
        when(taxGroupRepository.findOneWithNotFoundDetection(5L)).thenReturn(newGroup);

        // Config flags: allow edits, not used
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("allow-charge-taxgroup-edit"))).thenReturn(1);

        // Usage counts all zero
        when(jdbcTemplate.queryForObject(Mockito.startsWith("select count(1) from m_loan_charge"), eq(Long.class), anyLong()))
                .thenReturn(0L);
        when(jdbcTemplate.queryForObject(Mockito.startsWith("select count(1) from m_savings_account_charge"), eq(Long.class), anyLong()))
                .thenReturn(0L);
        when(jdbcTemplate.queryForObject(Mockito.startsWith("select count(1) from m_client_charge"), eq(Long.class), anyLong()))
                .thenReturn(0L);
        when(jdbcTemplate.queryForObject(Mockito.startsWith("select count(1) from m_share_account_charge"), eq(Long.class), anyLong()))
                .thenReturn(0L);

        // Act
        CommandProcessingResult result = service.updateCharge(10L, command);

        // Assert
        Map<String, Object> changes = result.getChanges();
        assertThat(changes).isNotNull();
        assertThat(changes.get("previousTaxGroupId")).isEqualTo(3L);
        assertThat(changes.get("newTaxGroupId")).isEqualTo(5L);
        assertThat(changes.get("warnings")).isNull();
    }

    @Test
    void updateCharge_removesTaxGroup_whenAllowedAndUsedWithEditsEnabled() {
        // Arrange
        JsonCommand command = Mockito.mock(JsonCommand.class);
        when(command.parameterExists("taxGroupId")).thenReturn(true);
        // Null/blank indicates removal
        when(command.stringValueOfParameterNamed("taxGroupId")).thenReturn(null);
        when(command.json()).thenReturn("{}");
        when(command.commandId()).thenReturn(2L);

        Charge charge = Mockito.mock(Charge.class);
        TaxGroup existingGroup = Mockito.mock(TaxGroup.class);
        when(existingGroup.getId()).thenReturn(7L);
        when(charge.getTaxGroup()).thenReturn(existingGroup);
        when(chargeRepository.findById(11L)).thenReturn(Optional.of(charge));
        when(chargeRepository.save(any(Charge.class))).thenReturn(charge);

        // Config flags: allow edits, allow when used
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("allow-charge-taxgroup-edit"))).thenReturn(1);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("allow-charge-taxgroup-edit-if-used"))).thenReturn(1);

        // Usage counts > 0
        when(jdbcTemplate.queryForObject(Mockito.startsWith("select count(1) from m_loan_charge"), eq(Long.class), anyLong()))
                .thenReturn(2L);
        when(jdbcTemplate.queryForObject(Mockito.startsWith("select count(1) from m_savings_account_charge"), eq(Long.class), anyLong()))
                .thenReturn(3L);
        when(jdbcTemplate.queryForObject(Mockito.startsWith("select count(1) from m_client_charge"), eq(Long.class), anyLong()))
                .thenReturn(1L);
        when(jdbcTemplate.queryForObject(Mockito.startsWith("select count(1) from m_share_account_charge"), eq(Long.class), anyLong()))
                .thenReturn(0L);

        // Act
        CommandProcessingResult result = service.updateCharge(11L, command);

        // Assert
        Map<String, Object> changes = result.getChanges();
        assertThat(changes).isNotNull();
        assertThat(changes.get("previousTaxGroupId")).isEqualTo(7L);
        assertThat(changes.get("newTaxGroupId")).isNull();
        assertThat((List<?>) changes.get("warnings")).isNotEmpty();
    }

    @Test
    void updateCharge_rejected_whenEditingDisabledByConfig() {
        // Arrange
        JsonCommand command = Mockito.mock(JsonCommand.class);
        when(command.parameterExists("taxGroupId")).thenReturn(true);
        when(command.stringValueOfParameterNamed("taxGroupId")).thenReturn("9");
        when(command.json()).thenReturn("{}");

        Charge charge = Mockito.mock(Charge.class);
        when(charge.getTaxGroup()).thenReturn(null);
        when(chargeRepository.findById(12L)).thenReturn(Optional.of(charge));

        // Config flags: disallow edits
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("allow-charge-taxgroup-edit"))).thenReturn(0);

        // Act + Assert
        assertThrows(org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException.class,
                () -> service.updateCharge(12L, command));
    }
}
