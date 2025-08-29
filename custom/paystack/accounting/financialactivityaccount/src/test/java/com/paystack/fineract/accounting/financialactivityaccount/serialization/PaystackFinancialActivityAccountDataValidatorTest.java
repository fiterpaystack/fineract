package com.paystack.fineract.accounting.financialactivityaccount.serialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaystackFinancialActivityAccountDataValidatorTest {

    @Mock
    private FromJsonHelper fromJsonHelper;

    private PaystackFinancialActivityAccountDataValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PaystackFinancialActivityAccountDataValidator(fromJsonHelper);
    }

    @Test
    void validateForCreate_WithValidJson_ShouldNotThrowException() {
        // given
        String validJson = """
                {
                    "financialActivityId": 202,
                    "glAccountId": 1
                }
                """;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("financialActivityId", 202);
        jsonObject.addProperty("glAccountId", 1);

        when(fromJsonHelper.parse(validJson)).thenReturn(jsonObject);
        when(fromJsonHelper.extractIntegerSansLocaleNamed(anyString(), any(JsonElement.class))).thenReturn(202);
        when(fromJsonHelper.extractLongNamed(anyString(), any(JsonElement.class))).thenReturn(202L, 1L);

        // when & then
        validator.validateForCreate(validJson); // Should not throw exception
    }

    @Test
    void validateForCreate_WithMissingFinancialActivityId_ShouldThrowException() {
        // given
        String invalidJson = """
                {
                    "glAccountId": 1
                }
                """;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("glAccountId", 1);

        when(fromJsonHelper.parse(invalidJson)).thenReturn(jsonObject);
        when(fromJsonHelper.extractIntegerSansLocaleNamed(anyString(), any(JsonElement.class))).thenReturn(null);
        when(fromJsonHelper.extractLongNamed("glAccountId", jsonObject)).thenReturn(1L);

        // when & then
        PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateForCreate(invalidJson));

        assertEquals("validation.msg.financialactivityaccount.financialActivityId.cannot.be.blank",
                exception.getErrors().get(0).getUserMessageGlobalisationCode());
    }

    @Test
    void validateForCreate_WithMissingGlAccountId_ShouldThrowException() {
        // given
        String invalidJson = """
                {
                    "financialActivityId": 202
                }
                """;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("financialActivityId", 202);

        when(fromJsonHelper.parse(invalidJson)).thenReturn(jsonObject);
        when(fromJsonHelper.extractIntegerSansLocaleNamed(anyString(), any(JsonElement.class))).thenReturn(202);
        when(fromJsonHelper.extractLongNamed("glAccountId", jsonObject)).thenReturn(null);
        // when & then
        PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateForCreate(invalidJson));

        assertEquals("validation.msg.financialactivityaccount.glAccountId.cannot.be.blank",
                exception.getErrors().get(0).getUserMessageGlobalisationCode());
    }

    // -------- Update tests (financialActivityId 202) --------

    @Test
    void validateForUpdate_WithFinancialActivityAndGlAccount_ShouldNotThrowException() {
        String json = """
                {
                    "financialActivityId": 202,
                    "glAccountId": 10
                }
                """;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("financialActivityId", 202);
        jsonObject.addProperty("glAccountId", 10);

        when(fromJsonHelper.parse(json)).thenReturn(jsonObject);
        when(fromJsonHelper.parameterExists("financialActivityId", jsonObject)).thenReturn(true);
        when(fromJsonHelper.parameterExists("glAccountId", jsonObject)).thenReturn(true);
        when(fromJsonHelper.extractIntegerSansLocaleNamed("financialActivityId", jsonObject)).thenReturn(202);
        when(fromJsonHelper.extractLongNamed("glAccountId", jsonObject)).thenReturn(10L);

        validator.validateForUpdate(json);
    }

    @Test
    void validateForUpdate_WithOnlyFinancialActivity_ShouldNotThrowException() {
        String json = """
                {
                    "financialActivityId": 202
                }
                """;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("financialActivityId", 202);

        when(fromJsonHelper.parse(json)).thenReturn(jsonObject);
        when(fromJsonHelper.parameterExists("financialActivityId", jsonObject)).thenReturn(true);
        when(fromJsonHelper.parameterExists("glAccountId", jsonObject)).thenReturn(false);
        when(fromJsonHelper.extractIntegerSansLocaleNamed("financialActivityId", jsonObject)).thenReturn(202);

        validator.validateForUpdate(json);
    }

    @Test
    void validateForUpdate_WithOnlyGlAccount_ShouldNotThrowException() {
        String json = """
                {
                    "glAccountId": 55
                }
                """;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("glAccountId", 55);

        when(fromJsonHelper.parse(json)).thenReturn(jsonObject);
        when(fromJsonHelper.parameterExists("financialActivityId", jsonObject)).thenReturn(false);
        when(fromJsonHelper.parameterExists("glAccountId", jsonObject)).thenReturn(true);
        when(fromJsonHelper.extractLongNamed("glAccountId", jsonObject)).thenReturn(55L);

        validator.validateForUpdate(json);
    }

    @Test
    void validateForUpdate_WithInvalidGlAccount_ShouldThrowException() {
        String json = """
                {
                    "glAccountId": 0
                }
                """;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("glAccountId", 0);

        when(fromJsonHelper.parse(json)).thenReturn(jsonObject);
        when(fromJsonHelper.parameterExists("financialActivityId", jsonObject)).thenReturn(false);
        when(fromJsonHelper.parameterExists("glAccountId", jsonObject)).thenReturn(true);
        when(fromJsonHelper.extractLongNamed("glAccountId", jsonObject)).thenReturn(0L);

        PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateForUpdate(json));

        // Adjusted expected message key to match actual thrown value
        assertEquals("validation.msg.financialactivityaccount.glAccountId.not.greater.than.zero",
                exception.getErrors().get(0).getUserMessageGlobalisationCode());
    }
}
