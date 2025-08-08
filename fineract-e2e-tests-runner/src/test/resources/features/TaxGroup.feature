@Tax @TaxGroup
Feature: Tax Group
  As a financial institution
  I want to manage tax groups
  So that I can apply multiple taxes together to financial products

  Background:
    And I have created multiple tax components:
      | name         | percentage |
      | VAT          | 10         |
      | Service Tax  | 5          |
      | GST          | 18         |
      | Cess         | 2          |

  Scenario: Create a new tax group
    When I create a tax group with:
      | name          | Combined Tax |
      | taxComponents | VAT, Service Tax |
    Then the tax group should be created successfully
    And the response should contain the tax group ID

  Scenario: Retrieve a specific tax group
    Given I have created a tax group named "GST Group" with components "GST, Cess"
    When I retrieve the tax group by ID
    Then I should see the tax group details:
      | name | GST Group |
    And the tax group should contain 2 tax components

  Scenario: Update tax group by adding new component
    Given I have created a tax group named "Basic Tax" with component "VAT"
    When I add "Service Tax" component to the tax group with future start date
    Then the tax group should be updated successfully
    And the tax group should now contain 2 tax components

  Scenario: Complex tax group with multiple components and dates
    Given I have created multiple tax components:
        | name          | percentage |
        | VAT1          | 10         |
        | GST1          | 18         |
        | Cess1         | 2          |
    And I create a tax group with complex components with start date as of today:
      | component     | startDateDelta  |
      | VAT1          | 0               |
      | GST1          | 6               |
      | Cess1         | 12              |
    When I update the tax group to set end dates for component by delta:
      | component     | endDateDelta  |
      | VAT1          | 2             |
    Then the tax group should correctly handle the complex date transitions
    And effective tax rates should change based on the date

  Scenario: Tax group effective rate calculation
    Given I have created a tax group named "Total Tax" with components
      | component    | percentage |
      | GST          | 18         |
      | Cess         | 2          |
      | Service Tax  | 5          |
    When I check the effective tax rate
    Then the total effective rate should be 25%
