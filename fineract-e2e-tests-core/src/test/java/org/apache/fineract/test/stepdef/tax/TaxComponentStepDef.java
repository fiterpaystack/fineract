/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.test.stepdef.tax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.fineract.client.models.GLAccountData;
import org.apache.fineract.client.models.GetTaxesComponentsResponse;
import org.apache.fineract.client.models.PostTaxesComponentsRequest;
import org.apache.fineract.client.models.PostTaxesComponentsResponse;
import org.apache.fineract.client.models.PutTaxesComponentsTaxComponentIdRequest;
import org.apache.fineract.client.models.PutTaxesComponentsTaxComponentIdResponse;
import org.apache.fineract.client.models.TaxComponentData;
import org.apache.fineract.client.services.TaxComponentsApi;
import org.apache.fineract.test.factory.TaxComponentRequestFactory;
import org.apache.fineract.test.helper.ErrorHelper;
import org.apache.fineract.test.stepdef.AbstractStepDef;
import org.apache.fineract.test.support.TestContextKey;
import org.springframework.beans.factory.annotation.Autowired;
import retrofit2.Response;

public class TaxComponentStepDef extends AbstractStepDef {

    @Autowired
    private TaxComponentRequestFactory taxComponentRequestFactory;

    @Autowired
    private TaxComponentsApi taxComponentApi;

    private static final String DATE_FORMAT = "dd MMMM yyyy";
    private static final String DEFAULT_LOCALE = "en";

    private Map<String, Long> createdTaxComponents = new HashMap<>();
    private Long lastCreatedTaxComponentId;
    private Response<PostTaxesComponentsResponse> lastTaxComponentResponse;
    private Object lastRetrievedData;

    private Exception lastError;

    @When("I create a tax component with:")
    public void createTaxComponent(DataTable dataTable) throws IOException {
        Map<String, String> data = dataTable.asMap();

        PostTaxesComponentsRequest request = taxComponentRequestFactory.defaultTaxComponentRequest();
        request.setName(data.get("name"));
        request.setPercentage(Float.parseFloat(data.get("percentage")));

        if (data.containsKey("startDate")) {
            String startDate = data.get("startDate");
            if (startDate.contains("month from now")) {
                startDate = LocalDate.now(ZoneId.systemDefault()).plusMonths(1).format(DateTimeFormatter.ofPattern(DATE_FORMAT));
            }
            request.setStartDate(startDate);
        }

        if (data.containsKey("debitAccountType")) {
            request.setDebitAccountType(2); // 2 - Asset
            request.setDebitAccountId(testContext().get("assetAccountId"));
        }

        if (data.containsKey("creditAccountType")) {
            request.setCreditAccountType(1);
            request.setCreditAccountId(testContext().get("liabilityAccountId"));
        }

        lastTaxComponentResponse = taxComponentApi.createTaxComponent(request).execute();
        ErrorHelper.checkSuccessfulApiCall(lastTaxComponentResponse);

        testContext().set(TestContextKey.TAX_COMPONENT_CREATED_RESPONSE, lastTaxComponentResponse);

        lastCreatedTaxComponentId = lastTaxComponentResponse.body().getResourceId();

        if (data.containsKey("name")) {
            createdTaxComponents.put(data.get("name"), lastCreatedTaxComponentId);
            testContext().set(TestContextKey.CREATED_TAX_COMPONENTS, createdTaxComponents);

        }

    }

    @When("I try to create a tax component without a name")
    public void tryToCreateTaxComponentWithoutName() {
        try {
            PostTaxesComponentsRequest request = new PostTaxesComponentsRequest();
            request.setPercentage(10f);
            request.setDateFormat(DATE_FORMAT);
            request.setLocale(DEFAULT_LOCALE);
            request.setStartDate(LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(DATE_FORMAT)));
            // Intentionally not setting name

            lastTaxComponentResponse = taxComponentApi.createTaxComponent(request).execute();
            if (lastTaxComponentResponse.isSuccessful()) {
                fail("Expected validation error but request succeeded");
            } else {
                // Record the error response for validation
                lastError = new RuntimeException("API call failed with status: " + lastTaxComponentResponse.code() + ", message: "
                        + lastTaxComponentResponse.errorBody().string());
            }

        } catch (Exception e) {
            lastError = e;
        }
    }

    @When("I try to create a tax component with:")
    public void createTaxComponentWithWrongData(DataTable dataTable) {
        Map<String, String> data = dataTable.asMap();

        PostTaxesComponentsRequest request = taxComponentRequestFactory.defaultTaxComponentRequest();
        request.setName(data.get("name"));
        request.setPercentage(Float.parseFloat(data.get("percentage")));

        try {
            Response<PostTaxesComponentsResponse> response = taxComponentApi.createTaxComponent(request).execute();
            if (response.isSuccessful()) {
                fail("Expected validation error but request succeeded");
            } else {
                // Record the error response for validation
                lastError = new RuntimeException(
                        "API call failed with status: " + response.code() + ", message: " + response.errorBody().string());
            }
        } catch (Exception e) {
            lastError = e;
        }

    }

    @Given("I have created a tax component named {string} with {float}% rate")
    public void createNamedTaxComponent(String name, float percentage) throws Exception {
        PostTaxesComponentsRequest request = taxComponentRequestFactory.customTaxComponent(name, percentage);

        Response<PostTaxesComponentsResponse> response = taxComponentApi.createTaxComponent(request).execute();
        assertNotNull(response.body());
        ErrorHelper.checkSuccessfulApiCall(response);
        lastCreatedTaxComponentId = response.body().getResourceId();
        testContext().set(TestContextKey.TAX_COMPONENT_CREATED_RESPONSE, lastTaxComponentResponse);

        createdTaxComponents.put(name, lastCreatedTaxComponentId);
        testContext().set(TestContextKey.CREATED_TAX_COMPONENTS, createdTaxComponents);

    }

    @Given("I have created multiple tax components:")
    public void createMultipleTaxComponents(DataTable dataTable) throws Exception {
        List<Map<String, String>> components = dataTable.asMaps();

        for (Map<String, String> component : components) {
            String name = component.get("name");
            float percentage = Float.parseFloat(component.get("percentage"));

            PostTaxesComponentsRequest request = taxComponentRequestFactory.customTaxComponent(name, percentage);

            Response<PostTaxesComponentsResponse> response = taxComponentApi.createTaxComponent(request).execute();
            ErrorHelper.checkSuccessfulApiCall(response);
            lastTaxComponentResponse = response;

            Long resourceId = response.body().getResourceId();
            assertNotNull(resourceId, "Resource ID should not be null for created tax component");
            createdTaxComponents.put(name, resourceId);
        }

        testContext().set(TestContextKey.CREATED_TAX_COMPONENTS, createdTaxComponents);
        testContext().set(TestContextKey.TAX_COMPONENT_CREATED_RESPONSE, lastTaxComponentResponse);
    }

    @When("I retrieve the tax component by ID")
    public void retrieveTaxComponentById() throws Exception {
        assertNotNull(lastCreatedTaxComponentId, "No tax component ID available");
        lastRetrievedData = taxComponentApi.retrieveTaxComponent(lastCreatedTaxComponentId).execute();
    }

    @When("I update the tax component percentage to {float}%")
    public void updateTaxComponentPercentage(float percentage) throws Exception {
        PutTaxesComponentsTaxComponentIdRequest request = new PutTaxesComponentsTaxComponentIdRequest();
        request.setPercentage(percentage);

        Response<PutTaxesComponentsTaxComponentIdResponse> taxComponentUpdateResponse = taxComponentApi
                .updateTaxCompoent(lastCreatedTaxComponentId, request).execute();
        ErrorHelper.checkSuccessfulApiCall(taxComponentUpdateResponse);

    }

    @When("I update the percentage multiple times:")
    public void updatePercentageMultipleTimes(DataTable dataTable) throws Exception {
        List<Map<String, String>> updates = dataTable.asMaps();

        for (Map<String, String> update : updates) {
            String percentage = update.get("percentage");

            PutTaxesComponentsTaxComponentIdRequest request = new PutTaxesComponentsTaxComponentIdRequest();
            request.setPercentage(Float.parseFloat(percentage));
            request.setDateFormat(DATE_FORMAT);
            request.setLocale(DEFAULT_LOCALE);

            Response<PutTaxesComponentsTaxComponentIdResponse> response = taxComponentApi
                    .updateTaxCompoent(lastCreatedTaxComponentId, request).execute();
            ErrorHelper.checkSuccessfulApiCall(response);
        }
    }

    @When("I retrieve the tax component template")
    public void retrieveTaxComponentTemplate() throws Exception {
        lastRetrievedData = taxComponentApi.retrieveTemplate21().execute();
    }

    @Then("the tax component should be created successfully")
    public void verifyTaxComponentCreated() {
        assertNotNull(lastTaxComponentResponse);
        assertNotNull(lastTaxComponentResponse.body());
        assertNotNull(lastTaxComponentResponse.body().getResourceId());
        assertTrue(lastTaxComponentResponse.body().getResourceId() > 0);
    }

    @Then("the response should contain the tax component ID")
    public void verifyResponseContainsTaxComponentId() {
        assertNotNull(lastTaxComponentResponse, "No tax component response available");
        assertNotNull(lastTaxComponentResponse.body().getResourceId(), "Response does not contain resource ID");
        assertTrue(lastTaxComponentResponse.body().getResourceId() > 0, "Resource ID should be positive");
    }

    @Then("I should see the tax component details:")
    public void verifyTaxComponentDetails(DataTable dataTable) {
        assertNotNull(lastRetrievedData, "No tax component data retrieved");

        Map<String, String> expectedDetails = dataTable.asMap(String.class, String.class);
        GetTaxesComponentsResponse actualData = (GetTaxesComponentsResponse) ((Response<?>) lastRetrievedData).body();

        String expectedName = expectedDetails.get("name");
        assertNotNull(expectedName, "Expected name must be provided in the data table");

        for (Map.Entry<String, String> entry : expectedDetails.entrySet()) {
            String field = entry.getKey();
            String expectedValue = entry.getValue();
            Object actualValue = switch (field) {
                case "id" -> actualData.getId();
                case "name" -> actualData.getName();
                case "percentage" -> actualData.getPercentage();
                default -> throw new IllegalArgumentException("Unexpected field: " + field);
            };

            assertNotNull(actualValue, "Field '" + field + "' was not found or is null");

            if (field.equals("percentage")) {
                double expected = Double.parseDouble(expectedValue);
                double actual = ((Number) actualValue).doubleValue();
                assertEquals(expected, actual, 0.01, "Mismatch in field: " + field);
            } else {
                assertEquals(expectedValue, actualValue.toString(), "Mismatch in field: " + field);
            }
        }
    }

    @Then("the tax component should be updated successfully")
    public void verifyTaxComponentUpdatedSuccessfully() {
        // If no error was thrown, the update was successful
        assertNull(lastError, "Tax component update failed with error: " + (lastError != null ? lastError.getMessage() : ""));
    }

    @Then("the current percentage should be {float}%")
    public void verifyCurrentPercentage(double expectedPercentage) throws Exception {

        Response<GetTaxesComponentsResponse> response = taxComponentApi.retrieveTaxComponent(lastCreatedTaxComponentId).execute();
        ErrorHelper.checkSuccessfulApiCall(response);

        float actual = response.body().getPercentage();

        assertEquals(expectedPercentage, actual, 0.01,
                String.format("Expected percentage to be %.2f but was %.2f", expectedPercentage, actual));
    }

    @Then("the tax component should have both debit and credit accounts configured")
    public void verifyBothAccountsConfigured() throws Exception {
        Response<GetTaxesComponentsResponse> response = taxComponentApi.retrieveTaxComponent(lastCreatedTaxComponentId).execute();
        ErrorHelper.checkSuccessfulApiCall(response);

        GetTaxesComponentsResponse taxesComponentData = response.body();
        assertNotNull(taxesComponentData, "Tax component data not found");

        assertNotNull(taxesComponentData.getDebitAccount(), "Debit account not configured");
        assertNotNull(taxesComponentData.getCreditAccount(), "Credit account not configured");

    }

    @Then("the tax component should have a history of all changes")
    public void verifyTaxComponentHistory() throws Exception {
        Response<GetTaxesComponentsResponse> response = taxComponentApi.retrieveTaxComponent(lastCreatedTaxComponentId).execute();
        ErrorHelper.checkSuccessfulApiCall(response);

        Collection<Object> histories = response.body().getTaxComponentHistories();
        assertNotNull(histories, "Tax component histories not found");
        assertTrue(histories.size() >= 3, "Should have at least 3 history entries after multiple updates");

    }

    @Then("the tax component should be created with a future effective date")
    public void verifyFutureEffectiveDate() throws Exception {
        Response<GetTaxesComponentsResponse> taxComponentData = taxComponentApi.retrieveTaxComponent(lastCreatedTaxComponentId).execute();
        ErrorHelper.checkSuccessfulApiCall(taxComponentData);

        LocalDate startDate = taxComponentData.body().getStartDate();
        assertNotNull(startDate, "Start date not found");

        LocalDate today = LocalDate.now(ZoneId.systemDefault());

        assertTrue(startDate.isAfter(today), String.format("Start date %s should be in the future (after %s)", startDate, today));
    }

    @Then("I should receive a validation error")
    public void verifyValidationError() {
        assertNotNull(lastError, "Expected a validation error but none occurred");

        String errorMessage = lastError.getMessage();
        assertTrue(errorMessage.contains("400") || errorMessage.contains("Bad Request") || errorMessage.contains("validation"),
                "Expected validation error but got: " + errorMessage);
    }

    @Then("the error should indicate that name is required")
    public void verifyNameRequiredError() {
        assertNotNull(lastError, "No error available to verify");

        String errorMessage = lastError.getMessage().toLowerCase(Locale.ROOT);
        assertTrue(
                errorMessage.contains("name") && (errorMessage.contains("required") || errorMessage.contains("mandatory")
                        || errorMessage.contains("must not be") || errorMessage.contains("may not be")),
                "Error should indicate name is required. Actual: " + lastError.getMessage());
    }

    @Then("the error should indicate that percentage must be positive")
    public void verifyPositivePercentageError() {
        assertNotNull(lastError, "No error available to verify");

        String errorMessage = lastError.getMessage().toLowerCase(Locale.ROOT);
        assertTrue(errorMessage.contains("`percentage` must be greater than 0"),
                "Error should indicate percentage must be positive. Actual: " + lastError.getMessage());
    }

    @Then("I should receive available GL account options")
    public void verifyGLAccountOptions() {
        assertNotNull(lastRetrievedData, "No template data retrieved");

        Response<TaxComponentData> templateData = (Response<TaxComponentData>) lastRetrievedData;
        assertNotNull(templateData.body().getGlAccountOptions(), "GL account options not found in template");
        assertTrue(templateData.body().getGlAccountOptions().size() > 1, "GL account options is empty");

    }

    @Then("the template should contain account type options")
    public void verifyAccountTypeOptions() {
        Response<TaxComponentData> templateData = (Response<TaxComponentData>) lastRetrievedData;

        // Check for account type options in the GL accounts
        Map<String, List<GLAccountData>> glAccountOptions = templateData.body().getGlAccountOptions();
        assertNotNull(templateData.body().getGlAccountOptions(), "GL account options not found in template");

        // Verify that different account types are present
        boolean hasAssetAccounts = false;
        boolean hasLiabilityAccounts = false;

        for (Map.Entry<String, List<GLAccountData>> entry : glAccountOptions.entrySet()) {
            String accountType = (String) entry.getKey();
            if ("assetAccountOptions".equals(accountType)) {
                hasAssetAccounts = true;
            } else if ("liabilityAccountOptions".equals(accountType)) {
                hasLiabilityAccounts = true;
            }
        }

        assertTrue(hasAssetAccounts || hasLiabilityAccounts, "Template should contain account type options (ASSET and/or LIABILITY)");
    }

    @When("I retrieve all tax components")
    public void retrieveAllTaxComponents() throws IOException {
        Response<List<GetTaxesComponentsResponse>> taxesComponentsResponseResponse = taxComponentApi.retrieveAllTaxComponents().execute();
        ErrorHelper.checkSuccessfulApiCall(taxesComponentsResponseResponse);
        lastRetrievedData = taxesComponentsResponseResponse.body();
    }

    @Then("I should see at least {int} tax components in the list")
    public void checkNumberOfTaxComponentsReturned(Integer total) {

        assertNotNull(lastRetrievedData, "No tax components data retrieved");
        List<GetTaxesComponentsResponse> taxComponents = (List<GetTaxesComponentsResponse>) lastRetrievedData;
        assertTrue(taxComponents.size() >= total, "Expected at least " + total + " tax components but found " + taxComponents.size());

    }
}
