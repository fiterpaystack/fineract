@Tax @TaxComponent
Feature: Tax Component
  As a financial institution
  I want to manage tax components
  So that I can apply taxes to financial products and transactions

  Background:
    Given I have created a liability account for tax collection

  Scenario: Create a new tax component
    When I create a tax component with:
      | name       | VAT              |
      | percentage | 10               |
      | startDate  | 01 January 2023  |
    Then the tax component should be created successfully
    And the response should contain the tax component ID

  Scenario: Retrieve a specific tax component
    Given I have created a tax component named "GST" with 18% rate
    When I retrieve the tax component by ID
    Then I should see the tax component details:
      | name       | GST |
      | percentage | 18  |

  Scenario: Retrieve all tax components
    Given I have created multiple tax components:
      | name         | percentage |
      | Service Tax  | 12         |
      | Sales Tax    | 5          |
      | Import Duty  | 15         |
    When I retrieve all tax components
    Then I should see at least 3 tax components in the list

  Scenario: Update tax component percentage
    Given I have created a tax component named "Progressive Tax" with 10% rate
    When I update the tax component percentage to 15%
    Then the tax component should be updated successfully
    And the current percentage should be 15%

  Scenario: Create tax component with debit and credit accounts
    Given I have created an asset account for tax payment
    And I have created a liability account for tax collection
    When I create a tax component with:
      | name              | Customs Duty |
      | percentage        | 25           |
      | debitAccountType  | ASSET        |
      | creditAccountType | LIABILITY    |
    Then the tax component should have both debit and credit accounts configured

  Scenario: Tax component history tracking
    Given I have created a tax component named "Variable Tax" with 10% rate
    When I update the percentage multiple times:
      | percentage |
      | 15         |
      | 20         |
      | 25         |
    Then the tax component should have a history of all changes

  Scenario: Create tax component with future start date
    When I create a tax component with:
      | name       | Future Tax         |
      | percentage | 8                  |
      | startDate  | [1 month from now] |
    Then the tax component should be created with a future effective date

  Scenario: Validation - Create tax component without required fields
    When I try to create a tax component without a name
    Then I should receive a validation error
    And the error should indicate that name is required

  Scenario: Validation - Create tax component with negative percentage
    When I try to create a tax component with:
      | name       | Invalid Tax |
      | percentage | -5          |
    Then I should receive a validation error
    And the error should indicate that percentage must be positive

  Scenario: Retrieve tax component template
    When I retrieve the tax component template
    Then I should receive available GL account options
    And the template should contain account type options
