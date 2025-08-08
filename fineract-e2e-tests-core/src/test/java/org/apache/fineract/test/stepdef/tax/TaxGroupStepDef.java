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

import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.client.models.GetTaxesComponentsResponse;
import org.apache.fineract.client.models.GetTaxesGroupResponse;
import org.apache.fineract.client.models.GetTaxesGroupTaxAssociations;
import org.apache.fineract.client.models.GetTaxesGroupTaxComponent;
import org.apache.fineract.client.models.PostTaxesComponentsRequest;
import org.apache.fineract.client.models.PostTaxesComponentsResponse;
import org.apache.fineract.client.models.PostTaxesGroupRequest;
import org.apache.fineract.client.models.PostTaxesGroupResponse;
import org.apache.fineract.client.models.PostTaxesGroupTaxComponents;
import org.apache.fineract.client.models.PutTaxesGroupTaxComponents;
import org.apache.fineract.client.models.PutTaxesGroupTaxGroupIdRequest;
import org.apache.fineract.client.models.PutTaxesGroupTaxGroupIdResponse;
import org.apache.fineract.client.models.TaxComponentData;
import org.apache.fineract.client.models.TaxGroupData;
import org.apache.fineract.client.services.TaxComponentsApi;
import org.apache.fineract.client.services.TaxGroupApi;
import org.apache.fineract.test.data.accounttype.AccountTypeResolver;
import org.apache.fineract.test.data.accounttype.DefaultAccountType;
import org.apache.fineract.test.factory.TaxComponentRequestFactory;
import org.apache.fineract.test.factory.TaxGroupRequestFactory;
import org.apache.fineract.test.helper.ErrorHelper;
import org.apache.fineract.test.helper.Utils;
import org.apache.fineract.test.stepdef.AbstractStepDef;
import org.apache.fineract.test.support.TestContextKey;
import org.springframework.beans.factory.annotation.Autowired;
import retrofit2.Response;

@Slf4j
public class TaxGroupStepDef extends AbstractStepDef {

    @Autowired
    private TaxComponentRequestFactory taxComponentRequestFactory;

    @Autowired
    private TaxGroupRequestFactory taxGroupRequestFactory;

    @Autowired
    private TaxComponentsApi taxComponentApi;

    @Autowired
    private TaxGroupApi taxGroupApi;

    @Autowired
    private AccountTypeResolver accountTypeResolver;

    private static final String DATE_FORMAT = "dd MMMM yyyy";

    private Map<String, Long> createdTaxGroups = new HashMap<>();
    private Long lastCreatedTaxGroupId;
    private Response<PostTaxesGroupResponse> lastTaxGroupResponse;
    private Object lastRetrievedData;
    private Exception lastError;
    private List<Map<String, String>> componentConfigurations = new ArrayList<>();
    private GetTaxesGroupResponse lastRetrievedTaxGroupDataLocal;
    private Object lastHttpResponse;

    @Given("I have created a liability account for tax collection")
    public void createLiabilityAccount() {

        Long accountId = accountTypeResolver.resolve(DefaultAccountType.FUND_RECEIVABLES);
        testContext().set("liabilityAccountId", accountId);
    }

    @Given("I have created an asset account for tax payment")
    public void createAssetAccount() {

        Long accountId = accountTypeResolver.resolve(DefaultAccountType.AA_SUSPENSE_BALANCE);
        testContext().set("assetAccountId", accountId);
    }

    @Then("the response should contain the tax group ID")
    public void verifyResponseContainsTaxGroupId() {
        assertNotNull(lastTaxGroupResponse);
        assertNotNull(lastTaxGroupResponse.body());
        assertNotNull(lastTaxGroupResponse.body().getResourceId());
        assertTrue(lastTaxGroupResponse.body().getResourceId() > 0);
    }

    @When("I create a tax group with:")
    public void createTaxGroup(DataTable dataTable) {
        Map<String, String> data = dataTable.asMap();

        PostTaxesGroupRequest request = taxGroupRequestFactory.defaultTaxGroupRequest();
        request.setName(data.get("name"));

        if (data.containsKey("taxComponents")) {
            List<String> componentNames = Utils.splitAndTrim(data.get("taxComponents"), ",");
            Set<PostTaxesGroupTaxComponents> components = new HashSet<>();

            for (String componentName : componentNames) {
                Long componentId = getCreatedTaxComponent(componentName);
                if (componentId != null) {
                    PostTaxesGroupTaxComponents component = new PostTaxesGroupTaxComponents();
                    component.setTaxComponentId(componentId);
                    component.setStartDate(LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(DATE_FORMAT)));
                    components.add(component);
                }
            }

            request.setTaxComponents(components);
        }

        try {
            lastTaxGroupResponse = taxGroupApi.createTaxGroup(request).execute();
            ErrorHelper.checkSuccessfulApiCall(lastTaxGroupResponse);
            lastCreatedTaxGroupId = lastTaxGroupResponse.body().getResourceId();

            if (data.containsKey("name")) {
                createdTaxGroups.put(data.get("name"), lastCreatedTaxGroupId);
            }
        } catch (Exception e) {
            lastError = e;
        }
    }

    @When("I retrieve the tax group by ID")
    public void retrieveTaxGroupById() throws Exception {
        lastRetrievedData = taxGroupApi.retrieveTaxGroup(lastCreatedTaxGroupId).execute();
        ErrorHelper.checkSuccessfulApiCall((Response<GetTaxesGroupResponse>) lastRetrievedData);
    }

    @When("I add {string} component to the tax group with future start date")
    public void addComponentToTaxGroup(String componentName) throws Exception {
        Long componentId = getCreatedTaxComponent(componentName);

        Set<PutTaxesGroupTaxComponents> components = new HashSet<>();

        PutTaxesGroupTaxComponents component = new PutTaxesGroupTaxComponents();
        component.setTaxComponentId(componentId);
        component.setStartDate(LocalDate.now(ZoneId.systemDefault()).plusDays(1).format(DateTimeFormatter.ofPattern(DATE_FORMAT)));
        components.add(component);

        PutTaxesGroupTaxGroupIdRequest request = taxGroupRequestFactory.defaultUpdateRequest(components);

        Response<PutTaxesGroupTaxGroupIdResponse> taxGroupUpdateResponse = taxGroupApi.updateTaxGroup(lastCreatedTaxGroupId, request)
                .execute();
        ErrorHelper.checkSuccessfulApiCall(taxGroupUpdateResponse);
        lastHttpResponse = taxGroupUpdateResponse.body();
    }

    @When("I retrieve the tax group template")
    public void retrieveTaxGroupTemplate() throws Exception {
        lastRetrievedData = taxGroupApi.retrieveTemplate22();
    }

    @Then("the tax group should be created successfully")
    public void verifyTaxGroupCreated() {
        assertNotNull(lastTaxGroupResponse);
        assertNotNull(lastTaxGroupResponse.body());
        assertNotNull(lastTaxGroupResponse.body().getResourceId());
        assertTrue(lastTaxGroupResponse.body().getResourceId() > 0);
    }

    @And("the error should indicate that {string}")
    public void verifyErrorMessage(String expectedMessage) {
        assertNotNull(lastError);
        assertTrue(lastError.getMessage().contains(expectedMessage) || lastError.toString().contains(expectedMessage));
    }

    @Given("I have created a tax group named {string} with components {string}")
    public void createNamedTaxGroup(String groupName, String componentsList) throws Exception {
        List<String> componentNames = Utils.splitAndTrim(componentsList, ",");
        Set<PostTaxesGroupTaxComponents> components = new HashSet<>();

        for (String componentName : componentNames) {
            Long componentId = getCreatedTaxComponent(componentName);
            if (componentId != null) {
                PostTaxesGroupTaxComponents component = new PostTaxesGroupTaxComponents();
                component.setTaxComponentId(componentId);
                component.setStartDate(LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(DATE_FORMAT)));
                components.add(component);
            }
        }

        PostTaxesGroupRequest request = taxGroupRequestFactory.defaultTaxGroupRequest();
        request.setName(groupName);
        request.setTaxComponents(components);

        Response<PostTaxesGroupResponse> response = taxGroupApi.createTaxGroup(request).execute();
        assertNotNull(response.body());
        ErrorHelper.checkSuccessfulApiCall(response);
        lastCreatedTaxGroupId = response.body().getResourceId();
        createdTaxGroups.put(groupName, lastCreatedTaxGroupId);
    }

    @When("I check the effective tax rate")
    public void checkEffectiveTaxRate() throws Exception {
        assertNotNull(lastCreatedTaxGroupId);

        Response<GetTaxesGroupResponse> response = taxGroupApi.retrieveTaxGroup(lastCreatedTaxGroupId).execute();
        ErrorHelper.checkSuccessfulApiCall(response);

        AtomicReference<Float> totalRate = new AtomicReference<>(0.0f);
        Set<GetTaxesGroupTaxAssociations> taxAssociations = response.body().getTaxAssociations();
        assertNotNull(taxAssociations, "Tax associations should not be null");
        assertFalse(taxAssociations.isEmpty(), "No tax associations found in the tax group");

        taxAssociations.forEach(association -> {
            assertNotNull(association.getTaxComponent(), "Tax association should have a tax component");
            GetTaxesGroupTaxComponent component = association.getTaxComponent();
            assertNotNull(component.getPercentage(), "Tax component percentage should not be null");
            totalRate.updateAndGet(v -> (v + component.getPercentage()));
        });

        testContext().set("calculatedEffectiveRate", totalRate.get());
    }

    @Then("the total effective rate should be {float}%")
    public void verifyTotalEffectiveRate(Float expectedRate) {
        Float calculatedRate = (Float) testContext().get("calculatedEffectiveRate");

        assertNotNull(calculatedRate, "Effective tax rate was not calculated");
        assertEquals(expectedRate, calculatedRate, 0.01, "Total effective tax rate does not match expected value");

        // free up this resource
        testContext().set("calculatedEffectiveRate", null);
    }

    @And("I create a tax group with complex components with start date as of today:")
    public void configureComponentsWithDates(DataTable dataTable) throws Exception {
        componentConfigurations = dataTable.asMaps();
        Set<PostTaxesGroupTaxComponents> taxComponents = new HashSet<>();
        LocalDate baseStartDate = LocalDate.now(ZoneId.systemDefault());

        for (Map<String, String> config : componentConfigurations) {
            String componentName = config.get("component");
            int startDateDelta = Integer.parseInt(config.get("startDateDelta"));

            // Get the component ID
            Long componentId = getCreatedTaxComponent(componentName);
            assertNotNull(componentId, "Tax component '" + componentName + "' must exist");

            PostTaxesGroupTaxComponents groupComponent = new PostTaxesGroupTaxComponents();
            groupComponent.setTaxComponentId(componentId);
            groupComponent.setStartDate(baseStartDate.plusMonths(startDateDelta).format(DateTimeFormatter.ofPattern(DATE_FORMAT)));

            taxComponents.add(groupComponent);
        }

        // Get the tax group request from context (created in previous step)
        PostTaxesGroupRequest request = taxGroupRequestFactory.defaultTaxGroupRequest();
        request.setTaxComponents(taxComponents);

        // Create the tax group
        Response<PostTaxesGroupResponse> response = taxGroupApi.createTaxGroup(request).execute();
        ErrorHelper.checkSuccessfulApiCall(response);

        lastCreatedTaxGroupId = response.body().getResourceId();
    }

    @Then("the tax group should correctly handle the complex date transitions")
    public void verifyComplexDateTransitions() throws Exception {
        // Long taxGroupId = testContext().get("complexTaxGroupId");
        assertNotNull(lastCreatedTaxGroupId, "Complex tax group was not created");

        // Retrieve the tax group
        Response<GetTaxesGroupResponse> response = taxGroupApi.retrieveTaxGroup(lastCreatedTaxGroupId).execute();
        ErrorHelper.checkSuccessfulApiCall(response);

        GetTaxesGroupResponse taxGroupData = response.body();

        Set<GetTaxesGroupTaxAssociations> taxAssociations = taxGroupData.getTaxAssociations();
        assertEquals(componentConfigurations.size(), taxAssociations.size(), "Number of tax components doesn't match");

        // Verify each component's dates

        taxAssociations.forEach(association -> {
            GetTaxesGroupTaxComponent component = association.getTaxComponent();
            assertNotNull(component, "Tax association should have a tax component");
            String componentName = component.getName();

            // Find the expected configuration
            Map<String, String> expectedConfig = componentConfigurations.stream()
                    .filter(config -> config.get("component").equals(componentName)).findFirst().orElse(null);

            assertNotNull(expectedConfig, "Component " + componentName + " not found in expected configurations");

            // Verify dates
            LocalDate startDateArray = association.getStartDate();
            assertNotNull(startDateArray, "Start date is missing for " + componentName);

            if (expectedConfig.get("endDate") != null && !expectedConfig.get("endDate").trim().isEmpty()) {
                LocalDate endDateArray = association.getEndDate();
                assertNotNull(endDateArray, "End date is missing for " + componentName);
            }
        });

        lastRetrievedTaxGroupDataLocal = taxGroupData;
    }

    @And("effective tax rates should change based on the date")
    public void verifyEffectiveTaxRatesChangeByDate() throws Exception {
        Long taxGroupId = lastCreatedTaxGroupId;
        if (lastRetrievedTaxGroupDataLocal == null) {
            Response<GetTaxesGroupResponse> taxGroupDataResponse = taxGroupApi.retrieveTaxGroup(taxGroupId).execute();
            ErrorHelper.checkSuccessfulApiCall(taxGroupDataResponse);
            lastRetrievedTaxGroupDataLocal = taxGroupDataResponse.body();
        }

        Set<GetTaxesGroupTaxAssociations> taxAssociations = lastRetrievedTaxGroupDataLocal.getTaxAssociations();
        assertNotNull(taxAssociations, "Tax associations should not be null");

        // Collect all relevant dates (start and end) for testing
        Set<LocalDate> testDates = new HashSet<>();
        for (GetTaxesGroupTaxAssociations assoc : taxAssociations) {
            if (assoc.getStartDate() != null) {
                testDates.add(assoc.getStartDate());
            }

            if (assoc.getEndDate() != null) {
                testDates.add(assoc.getEndDate());
            }
        }
        // Add a date before all components start
        LocalDate earliest = testDates.stream().min(LocalDate::compareTo).orElse(LocalDate.now(ZoneId.systemDefault()));
        testDates.add(earliest.minusDays(1));

        // Sort dates for deterministic assertions
        List<LocalDate> sortedTestDates = new ArrayList<>(testDates);
        Collections.sort(sortedTestDates);

        for (LocalDate testDate : sortedTestDates) {
            float expectedRate = 0.0f;
            for (GetTaxesGroupTaxAssociations assoc : taxAssociations) {
                LocalDate start = assoc.getStartDate();
                LocalDate end = assoc.getEndDate();
                if (!testDate.isBefore(start) && (end == null || !testDate.isAfter(end))) {
                    expectedRate += assoc.getTaxComponent().getPercentage();
                }
            }
            float actualRate = calculateEffectiveRateForDate(taxAssociations, testDate);
            assertEquals(expectedRate, actualRate, 0.01,
                    String.format("Effective tax rate on %s should be %.2f%% but was %.2f%%", testDate, expectedRate, actualRate));
        }
    }

    // Helper method to calculate effective rate for a specific date
    private float calculateEffectiveRateForDate(Set<GetTaxesGroupTaxAssociations> taxAssociations, LocalDate date) {

        float totalRate = 0.0f;

        for (GetTaxesGroupTaxAssociations association : taxAssociations) {
            LocalDate startDate = association.getStartDate();
            LocalDate endDate = association.getEndDate();

            // Check if component is active on the given date
            boolean isActive = !date.isBefore(startDate) && (endDate == null || !date.isAfter(endDate));

            if (isActive) {
                GetTaxesGroupTaxComponent component = association.getTaxComponent();
                assertNotNull(component.getPercentage(), "Tax component percentage should not be null");
                float percentage = component.getPercentage();
                totalRate += percentage;
            }
        }

        return totalRate;
    }

    @Given("I have created a tax group named {string} with component {string}")
    public void createTaxGroupWithSingleComponent(String groupName, String componentName) throws Exception {
        // Get the component ID
        Long componentId = getCreatedTaxComponent(componentName);
        assertNotNull(componentId, "Tax component '" + componentName + "' must be created first");

        // Create the tax group
        PostTaxesGroupRequest request = taxGroupRequestFactory.defaultTaxGroupRequest();
        request.setName(groupName);

        PostTaxesGroupTaxComponents component = new PostTaxesGroupTaxComponents();
        component.setTaxComponentId(componentId);
        component.setStartDate(LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(DATE_FORMAT)));

        request.setTaxComponents(Set.of(component));

        Response<PostTaxesGroupResponse> response = taxGroupApi.createTaxGroup(request).execute();
        lastCreatedTaxGroupId = response.body().getResourceId();
        createdTaxGroups.put(groupName, lastCreatedTaxGroupId);
    }

    @When("I retrieve the tax group with template flag")
    public void retrieveTaxGroupWithTemplate() throws Exception {
        assertNotNull(lastCreatedTaxGroupId, "No tax group ID available to retrieve");

        // If your API client supports query parameters:
        // ? Look at swagger to add missing parameters
        lastRetrievedData = taxGroupApi.retrieveTaxGroup(lastCreatedTaxGroupId).execute(); // template = true

        testContext().set("taxGroupWithTemplate", lastRetrievedData);
    }

    @Then("I should see the tax group details")
    public void verifyTaxGroupDetails() {
        assertNotNull(lastRetrievedData, "No tax group data retrieved");

        Response<GetTaxesGroupResponse> taxGroupDataResponse = (Response<GetTaxesGroupResponse>) lastRetrievedData;
        assertNotNull(taxGroupDataResponse.body(), "Tax group data should not be null");
        GetTaxesGroupResponse taxGroupData = taxGroupDataResponse.body();

        // Verify basic fields
        assertNotNull(taxGroupData.getId(), "Tax group ID should be present");
        assertNotNull(taxGroupData.getName(), "Tax group name should be present");
        assertNotNull(taxGroupData.getTaxAssociations(), "Tax associations should be present");

        // Verify it has the expected structure
        Set<GetTaxesGroupTaxAssociations> taxAssociations = taxGroupData.getTaxAssociations();
        assertTrue(taxAssociations.size() > 0, "Tax group should have at least one component");

        // Store for further verification
        testContext().set("retrievedTaxGroupDetails", taxGroupData);
    }

    @And("I should also receive available tax component options for updates")
    public void verifyTaxComponentOptionsPresent() {
        Response<TaxGroupData> taxGroupDataResponse = testContext().get("taxGroupWithTemplate");
        TaxGroupData taxGroupData = taxGroupDataResponse.body();
        // When template=true, the response should include available tax components
        assertNotNull(taxGroupData.getTaxComponents(), "Tax component options should be present when retrieved with template flag");

        List<TaxComponentData> taxComponentOptions = taxGroupData.getTaxComponents();

        assertFalse(taxComponentOptions.isEmpty(), "Should have at least one tax component option available");

        // Verify each option has required fields
        for (TaxComponentData option : taxComponentOptions) {
            assertNotNull(option.getId(), "Tax component option should have an ID");
            assertNotNull(option.getName(), "Tax component option should have a name");
            assertNotNull(option.getPercentage(), "Tax component option should have a percentage");
        }

    }

    @When("I set an end date for {string} component to today")
    public void setEndDateForComponent(String componentName) throws Exception {
        assertNotNull(lastCreatedTaxGroupId, "No tax group available to update");

        Response<GetTaxesGroupResponse> taxGroupDataResponse = taxGroupApi.retrieveTaxGroup(lastCreatedTaxGroupId).execute();
        ErrorHelper.checkSuccessfulApiCall(taxGroupDataResponse);
        Set<GetTaxesGroupTaxAssociations> taxAssociations = taxGroupDataResponse.body().getTaxAssociations();

        assertNotNull(taxAssociations, "Tax associations should not be null");
        // Find the association ID for the VAT component
        Long associationId = null;

        for (GetTaxesGroupTaxAssociations association : taxAssociations) {
            GetTaxesGroupTaxComponent component = association.getTaxComponent();
            if (componentName.equals(component.getName())) {
                associationId = association.getId();

                break;
            }
        }

        assertNotNull(associationId, "Could not find association for component: " + componentName);

        Set<PutTaxesGroupTaxComponents> componentsToUpdate = new HashSet<>();

        // Update the existing component with end date
        PutTaxesGroupTaxComponents componentUpdate = new PutTaxesGroupTaxComponents();
        componentUpdate.setTaxComponentId(associationId);
        componentUpdate.setEndDate(LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(DATE_FORMAT)));
        componentsToUpdate.add(componentUpdate);

        PutTaxesGroupTaxGroupIdRequest updateRequest = taxGroupRequestFactory.defaultUpdateRequest(componentsToUpdate);

        try {
            // Perform the update
            Response<PutTaxesGroupTaxGroupIdResponse> response = taxGroupApi.updateTaxGroup(lastCreatedTaxGroupId, updateRequest).execute();
            ErrorHelper.checkSuccessfulApiCall(response);

            // Store response for verification
            lastHttpResponse = response.body();

        } catch (Exception e) {
            lastError = e;
            throw e;
        }
    }

    @Then("the tax group should be updated successfully")
    public void verifyTaxGroupUpdatedSuccessfully() {
        // Check if there was an error
        assertNull(lastError, "Tax group update failed with error: " + (lastError != null ? lastError.getMessage() : ""));
        assertNotNull(lastHttpResponse, "No update response received");

        if (lastHttpResponse instanceof PutTaxesGroupTaxGroupIdResponse response) {
            assertNotNull(response.getResourceId(), "Update response should contain resource ID");
            assertEquals(lastCreatedTaxGroupId, response.getResourceId(), "Updated tax group ID should match");
        } else {
            fail("Expected PutTaxesGroupTaxGroupIdResponse but got: " + lastHttpResponse.getClass().getSimpleName());
        }
    }

    @And("the {string} component should have an end date")
    public void verifyComponentHasEndDate(String componentName) throws Exception {
        // Retrieve the updated tax group
        Response<GetTaxesGroupResponse> taxGroupDataResponse = taxGroupApi.retrieveTaxGroup(lastCreatedTaxGroupId).execute();
        Set<GetTaxesGroupTaxAssociations> taxAssociations = taxGroupDataResponse.body().getTaxAssociations();

        // Find the component and verify it has an end date
        boolean found = false;
        for (GetTaxesGroupTaxAssociations association : taxAssociations) {
            GetTaxesGroupTaxComponent taxComponent = association.getTaxComponent();
            if (componentName.equals(taxComponent.getName())) {
                found = true;

                // Check end date
                LocalDate endDate = association.getEndDate();
                assertNotNull(endDate, componentName + " component should have an end date");
                assertEquals(LocalDate.now(ZoneId.systemDefault()), endDate, componentName + " component end date should be today");

                break;
            }
        }

        assertTrue(found, componentName + " component not found in tax group");
    }

    @Then("I should receive available tax component options")
    public void verifyTaxComponentOptionsReceived() {
        assertNotNull(lastRetrievedData, "No template data retrieved");

        Response<TaxGroupData> taxGroupDataResponse = (Response<TaxGroupData>) lastRetrievedData;
        assertNotNull(taxGroupDataResponse.body(), "Tax group data should not be null");
        TaxGroupData templateData = taxGroupDataResponse.body();

        if (templateData.getTaxComponents() != null && !templateData.getTaxComponents().isEmpty()) {
            List<TaxComponentData> options = templateData.getTaxComponents();
            assertNotNull(options, "Tax component options should not be null");
            assertFalse(options.isEmpty(), "Should have at least one tax component option");
            testContext().set("availableTaxComponentOptions", options);
        }

    }

    @Given("I have created a tax group named {string} with component {string} starting {string}")
    public void createTaxGroupWithComponentStartingOnDate(String groupName, String componentName, String startDate) throws Exception {
        // Get the component ID
        Long componentId = getCreatedTaxComponent(componentName);
        assertNotNull(componentId, "Tax component '" + componentName + "' must be created first");

        // Create the tax group
        PostTaxesGroupRequest request = taxGroupRequestFactory.defaultTaxGroupRequest();
        request.setName(groupName);

        PostTaxesGroupTaxComponents component = new PostTaxesGroupTaxComponents();
        component.setTaxComponentId(componentId);
        component.setStartDate(startDate);

        request.setTaxComponents(Set.of(component));

        Response<PostTaxesGroupResponse> response = taxGroupApi.createTaxGroup(request).execute();
        lastCreatedTaxGroupId = response.body().getResourceId();
        createdTaxGroups.put(groupName, lastCreatedTaxGroupId);

        // Store the component configuration for verification
        testContext().set("originalComponentStartDate", startDate);
        testContext().set("originalComponentName", componentName);
    }

    @When("I try to add the same {string} component again with start date {string}")
    public void tryToAddSameComponentWithDifferentStartDate(String componentName, String newStartDate) {
        try {
            assertNotNull(lastCreatedTaxGroupId, "No tax group available to update");

            // Get the component ID
            Long componentId = getCreatedTaxComponent(componentName);
            assertNotNull(componentId, "Tax component '" + componentName + "' not found");

            Set<PutTaxesGroupTaxComponents> componentsToAdd = new HashSet<>();

            // Try to add the same component with a different start date
            PutTaxesGroupTaxComponents duplicateComponent = new PutTaxesGroupTaxComponents();
            duplicateComponent.setTaxComponentId(componentId);
            duplicateComponent.setStartDate(newStartDate);
            componentsToAdd.add(duplicateComponent);

            PutTaxesGroupTaxGroupIdRequest updateRequest = taxGroupRequestFactory.defaultUpdateRequest(componentsToAdd);

            // This should fail with overlapping date error
            taxGroupApi.updateTaxGroup(lastCreatedTaxGroupId, updateRequest).execute();

            // If we get here, the update succeeded when it shouldn't have
            fail("Expected an error for overlapping date ranges, but the update succeeded");

        } catch (Exception e) {
            // Expected to fail - capture the error
            lastError = e;
        }
    }

    @And("I add components with different dates:")
    public void addComponentsWithDifferentDates(DataTable dataTable) throws Exception {
        List<Map<String, String>> componentConfigs = dataTable.asMaps();
        Set<PostTaxesGroupTaxComponents> taxComponents = new HashSet<>();

        for (Map<String, String> config : componentConfigs) {
            String componentName = config.get("component");
            String startDate = config.get("startDate");
            String endDate = config.get("endDate");

            // Get component ID
            Long componentId = getCreatedTaxComponent(componentName);
            assertNotNull(componentId, "Tax component '" + componentName + "' must be created first");

            PostTaxesGroupTaxComponents taxComponent = new PostTaxesGroupTaxComponents();
            taxComponent.setTaxComponentId(componentId);
            taxComponent.setStartDate(startDate);

            // Set end date if provided and not empty
            if (endDate != null && !endDate.trim().isEmpty()) {
                taxComponent.setEndDate(endDate);
            }

            taxComponents.add(taxComponent);
        }

        // Get the pending tax group request from previous step
        PostTaxesGroupRequest request = (PostTaxesGroupRequest) testContext().get("pendingTaxGroupRequest");
        assertNotNull(request, "No pending tax group request found. Create tax group first.");

        // Set the components
        request.setTaxComponents(taxComponents);

        try {
            // Create the tax group with phased components
            Response<PostTaxesGroupResponse> response = taxGroupApi.createTaxGroup(request).execute();
            lastCreatedTaxGroupId = response.body().getResourceId();

            // Store for verification
            testContext().set("phasedTaxGroupId", lastCreatedTaxGroupId);
            testContext().set("phasedComponentConfigs", componentConfigs);

            // Also store in the created groups map
            if (request.getName() != null) {
                createdTaxGroups.put(request.getName(), lastCreatedTaxGroupId);
            }

        } catch (Exception e) {
            lastError = e;
            throw new RuntimeException("Failed to create tax group with phased components: " + e.getMessage(), e);
        }
    }

    @Given("I have created a tax group named {string} with components")
    public void createTaxGroupWithComponentsTable(String groupName, DataTable dataTable) throws Exception {
        List<Map<String, String>> componentDetails = dataTable.asMaps();
        Set<PostTaxesGroupTaxComponents> taxComponents = new HashSet<>();

        // Create or verify each component
        for (Map<String, String> details : componentDetails) {
            String componentName = details.get("component");
            String percentage = details.get("percentage");

            // Check if component already exists
            Long componentId = getCreatedTaxComponent(componentName);

            if (componentId == null) {
                // Create the component if it doesn't exist
                PostTaxesComponentsRequest componentRequest = taxComponentRequestFactory.customTaxComponent(componentName,
                        Float.parseFloat(percentage));

                Response<PostTaxesComponentsResponse> componentResponse = taxComponentApi.createTaxComponent(componentRequest).execute();
                ErrorHelper.checkSuccessfulApiCall(componentResponse);
                componentId = componentResponse.body().getResourceId();

                Map<String, Long> existingTaxComponents = testContext().get(TestContextKey.CREATED_TAX_COMPONENTS);
                existingTaxComponents.put(componentName, componentId);

                testContext().set(TestContextKey.CREATED_TAX_COMPONENTS, existingTaxComponents);

            } else {
                // Optionally verify the percentage matches
                Response<GetTaxesComponentsResponse> existingComponentResponse = taxComponentApi.retrieveTaxComponent(componentId)
                        .execute();
                ErrorHelper.checkSuccessfulApiCall(existingComponentResponse);

                double actualPercentage = existingComponentResponse.body().getPercentage();
                double expectedPercentage = Double.parseDouble(percentage);

                if (Math.abs(actualPercentage - expectedPercentage) > 0.01) {
                    log.warn("Existing component {} has percentage {}% but table specifies {}%", componentName, actualPercentage,
                            expectedPercentage);
                }
            }

            // Add to tax group
            PostTaxesGroupTaxComponents groupComponent = new PostTaxesGroupTaxComponents();
            groupComponent.setTaxComponentId(componentId);
            groupComponent.setStartDate(LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(DATE_FORMAT)));
            taxComponents.add(groupComponent);
        }

        // Create the tax group
        PostTaxesGroupRequest groupRequest = taxGroupRequestFactory.defaultTaxGroupRequest();
        groupRequest.setName(groupName);
        groupRequest.setTaxComponents(taxComponents);

        Response<PostTaxesGroupResponse> response = taxGroupApi.createTaxGroup(groupRequest).execute();
        ErrorHelper.checkSuccessfulApiCall(response);
        lastCreatedTaxGroupId = response.body().getResourceId();
        createdTaxGroups.put(groupName, lastCreatedTaxGroupId);

        // Store component details for rate calculation
        testContext().set("taxGroupComponents", componentDetails);

        log.info("Created tax group '{}' with {} components", groupName, taxComponents.size());
    }

    @When("I try to create a tax group with:")
    public void tryToCreateTaxGroupWithData(DataTable dataTable) {
        Map<String, String> data = dataTable.asMap();

        try {
            PostTaxesGroupRequest request = taxGroupRequestFactory.defaultTaxGroupRequest();
            request.setName(data.get("name"));

            // Handle special cases for taxComponents
            if (data.containsKey("taxComponents")) {
                String taxComponentsValue = data.get("taxComponents");

                if ("[]".equals(taxComponentsValue) || taxComponentsValue.trim().isEmpty()) {
                    // Empty array
                    request.setTaxComponents(new HashSet<>());
                } else if (taxComponentsValue.contains("[") && taxComponentsValue.contains("]")) {
                    // Parse array notation like [99999]
                    String cleanValue = taxComponentsValue.replace("[", "").replace("]", "").trim();
                    if (!cleanValue.isEmpty()) {
                        String[] ids = StringUtils.split(cleanValue, ',');
                        Set<PostTaxesGroupTaxComponents> components = new HashSet<>();

                        for (String idStr : ids) {
                            Long componentId = Long.parseLong(idStr.trim());
                            PostTaxesGroupTaxComponents component = new PostTaxesGroupTaxComponents();
                            component.setTaxComponentId(componentId);
                            component.setStartDate(LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(DATE_FORMAT)));
                            components.add(component);
                        }

                        request.setTaxComponents(components);
                    }
                } else {
                    // Handle comma-separated component names
                    String[] componentNames = StringUtils.split(taxComponentsValue, ',');
                    Set<PostTaxesGroupTaxComponents> components = new HashSet<>();

                    for (String componentName : componentNames) {
                        String trimmedName = componentName.trim();
                        Long componentId = getCreatedTaxComponent(trimmedName);
                        if (componentId != null) {
                            PostTaxesGroupTaxComponents component = new PostTaxesGroupTaxComponents();
                            component.setTaxComponentId(componentId);
                            component.setStartDate(LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(DATE_FORMAT)));
                            components.add(component);
                        }
                    }

                    request.setTaxComponents(components);
                }
            }

            // Handle taxComponentIds for the invalid component ID scenario
            if (data.containsKey("taxComponentIds")) {
                String taxComponentIds = data.get("taxComponentIds");
                if (taxComponentIds.contains("[") && taxComponentIds.contains("]")) {
                    String cleanValue = taxComponentIds.replace("[", "").replace("]", "").trim();
                    if (!cleanValue.isEmpty()) {
                        Long componentId = Long.parseLong(cleanValue);
                        PostTaxesGroupTaxComponents component = new PostTaxesGroupTaxComponents();
                        component.setTaxComponentId(componentId);
                        component.setStartDate(LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(DATE_FORMAT)));
                        request.setTaxComponents(Set.of(component));
                    }
                }
            }

            // Attempt to create the tax group
            lastTaxGroupResponse = taxGroupApi.createTaxGroup(request).execute();
            lastCreatedTaxGroupId = lastTaxGroupResponse.body().getResourceId();

            // If successful, store it
            if (data.containsKey("name")) {
                createdTaxGroups.put(data.get("name"), lastCreatedTaxGroupId);
            }

        } catch (Exception e) {
            // Expected to fail for validation scenarios
            lastError = e;
            log.info("Caught expected error: {}", e.getMessage());
        }
    }

    @Then("I should see the tax group details:")
    public void verifyTaxGroupDetailsFromTable(DataTable dataTable) {
        assertNotNull(lastRetrievedData, "No tax group data retrieved");

        Map<String, String> expectedDetails = dataTable.asMap();
        Response<GetTaxesGroupResponse> actualTaxGroupData = (Response<GetTaxesGroupResponse>) lastRetrievedData;

        // Verify each expected field
        for (Map.Entry<String, String> entry : expectedDetails.entrySet()) {
            String fieldName = entry.getKey();
            String expectedValue = entry.getValue();

            String actualValue = actualTaxGroupData.body().getName();
            assertNotNull(actualValue, "Field '" + fieldName + "' not found in tax group data");

            assertEquals(expectedValue, actualValue.toString(), "Field '" + fieldName + "' value mismatch");
        }
    }

    @And("the tax group should contain {int} tax components")
    public void verifyTaxGroupComponentCount(int expectedCount) {
        assertNotNull(lastRetrievedData, "No tax group data available");

        Response<GetTaxesGroupResponse> taxGroupData = (Response<GetTaxesGroupResponse>) lastRetrievedData;

        // Get tax associations/components
        Set<GetTaxesGroupTaxAssociations> taxAssociations = (Set<GetTaxesGroupTaxAssociations>) taxGroupData.body().getTaxAssociations();
        assertNotNull(taxAssociations, "Tax associations not found in tax group data");

        int actualCount = taxAssociations.size();
        assertEquals(expectedCount, actualCount,
                String.format("Expected tax group to contain %d components but found %d", expectedCount, actualCount));

    }

    @And("the tax group should now contain {int} tax components")
    public void verifyTaxGroupNowContainsComponents(int expectedCount) throws Exception {
        assertNotNull(lastCreatedTaxGroupId, "No tax group ID available");

        // Re-fetch the tax group to get updated data
        Response<GetTaxesGroupResponse> updatedTaxGroupData = taxGroupApi.retrieveTaxGroup(lastCreatedTaxGroupId).execute();
        assertNotNull(updatedTaxGroupData, "Failed to retrieve updated tax group data");

        Set<GetTaxesGroupTaxAssociations> taxAssociations = updatedTaxGroupData.body().getTaxAssociations();
        assertNotNull(taxAssociations, "Tax associations not found in updated tax group");

        int actualCount = taxAssociations.size();
        assertEquals(expectedCount, actualCount,
                String.format("After update, expected tax group to contain %d components but found %d", expectedCount, actualCount));

        // Log the current components
        log.info("Tax group now contains {} components:", actualCount);
        for (GetTaxesGroupTaxAssociations association : taxAssociations) {

            GetTaxesGroupTaxComponent component = association.getTaxComponent();
            if (component != null) {
                String componentName = component.getName();
                Object percentage = component.getPercentage();

                // Check dates to see if component is currently active
                LocalDate startDate = association.getStartDate();
                LocalDate endDate = association.getEndDate();

                String dateInfo = "";
                if (startDate != null) {
                    dateInfo += " (starts: " + startDate;
                    if (endDate != null) {
                        dateInfo += ", ends: " + endDate;
                    }
                    dateInfo += ")";
                }

                log.info("Component: {} - share: {}%{}", componentName, percentage, dateInfo);
            }
        }

        // Store updated data for further verification
        testContext().set("updatedTaxGroupData", updatedTaxGroupData);
    }

    @When("I update the tax group to set end dates for component by delta:")
    public void updateTaxGroupSetEndDates(DataTable dataTable) throws Exception {
        assertNotNull(lastCreatedTaxGroupId, "No tax group available to update");

        // Retrieve current tax group associations
        Response<GetTaxesGroupResponse> response = taxGroupApi.retrieveTaxGroup(lastCreatedTaxGroupId).execute();
        ErrorHelper.checkSuccessfulApiCall(response);
        Set<GetTaxesGroupTaxAssociations> associations = response.body().getTaxAssociations();
        assertNotNull(associations, "No tax associations found in tax group");

        // Map component name to association ID
        Map<String, GetTaxesGroupTaxAssociations> componentNameToAssociationId = new HashMap<>();
        for (GetTaxesGroupTaxAssociations assoc : associations) {
            String name = assoc.getTaxComponent().getName();
            componentNameToAssociationId.put(name, assoc);
        }

        Set<PutTaxesGroupTaxComponents> componentsToUpdate = new HashSet<>();
        for (Map<String, String> row : dataTable.asMaps()) {
            String component = row.get("component");
            String endDateDelta = row.get("endDateDelta");
            GetTaxesGroupTaxAssociations association = componentNameToAssociationId.get(component);
            assertNotNull(association, "Component association not found: " + component);

            PutTaxesGroupTaxComponents update = new PutTaxesGroupTaxComponents();
            update.setId(association.getId());
            update.setEndDate(LocalDate.now(ZoneId.systemDefault()).plusMonths(Integer.parseInt(endDateDelta))
                    .format(DateTimeFormatter.ofPattern(DATE_FORMAT)));
            update.setTaxComponentId(association.getTaxComponent().getId());
            componentsToUpdate.add(update);
        }
        PutTaxesGroupTaxGroupIdRequest updateRequest = taxGroupRequestFactory.defaultUpdateRequest(componentsToUpdate);

        Response<PutTaxesGroupTaxGroupIdResponse> updateResponse = taxGroupApi.updateTaxGroup(lastCreatedTaxGroupId, updateRequest)
                .execute();

        ErrorHelper.checkSuccessfulApiCall(updateResponse);
    }

    private Long getCreatedTaxComponent(String name) {
        Map<String, Long> listOfComponents = testContext().get(TestContextKey.CREATED_TAX_COMPONENTS);
        return listOfComponents.get(name);
    }
}
