# Fee Discount Engine - API Documentation

## Overview

The Fee Discount Engine provides RESTful APIs for managing discount rules and analytics for savings products.

## Base URL

```
/api/v1/products/{productId}/discount-rules
/api/v1/products/{productId}/discount-analytics
```

## Authentication

All endpoints require authentication. Include the authentication token in the request headers:

```
Authorization: Bearer <token>
```

## Discount Rules API

### Get Discount Rules

**GET** `/api/v1/products/{productId}/discount-rules`

Retrieves all active discount rules for a specific product.

**Parameters:**
- `productId` (path): The ID of the savings product

**Response:**
```json
[
  {
    "id": 1,
    "productId": 1,
    "productType": "SAVINGS_PRODUCT",
    "name": "High Balance Discount",
    "description": "10% discount for accounts with balance > $10,000",
    "discountType": "PERCENTAGE",
    "discountValue": 10.0,
    "maxDiscountAmount": 100.0,
    "conditions": {
      "accountBalance": {
        "enabled": true,
        "minimumBalance": 10000,
        "maximumBalance": null,
        "balanceType": "CURRENT"
      }
    },
    "validFrom": "2024-01-01",
    "validTo": "2024-12-31",
    "isActive": true,
    "priority": 1
  }
]
```

### Create Discount Rule

**POST** `/api/v1/products/{productId}/discount-rules`

Creates a new discount rule for a product.

**Request Body:**
```json
{
  "name": "New Customer Discount",
  "description": "20% discount for new customers",
  "discountType": "PERCENTAGE",
  "discountValue": 20.0,
  "maxDiscountAmount": 50.0,
  "conditions": {
    "client": {
      "enabled": true,
      "clientType": "NEW",
      "minimumAccountAge": 0,
      "maximumAccountAge": 30
    }
  },
  "validFrom": "2024-01-01",
  "validTo": "2024-12-31",
  "priority": 2
}
```

**Response:**
```json
{
  "resourceId": 2,
  "changes": {
    "name": "New Customer Discount",
    "discountType": "PERCENTAGE",
    "discountValue": 20.0
  },
  "commandId": 123
}
```

### Update Discount Rule

**PUT** `/api/v1/products/{productId}/discount-rules/{ruleId}`

Updates an existing discount rule.

**Parameters:**
- `productId` (path): The ID of the savings product
- `ruleId` (path): The ID of the discount rule

**Request Body:**
```json
{
  "name": "Updated New Customer Discount",
  "discountValue": 25.0,
  "maxDiscountAmount": 75.0
}
```

**Response:**
```json
{
  "resourceId": 2,
  "changes": {
    "name": "Updated New Customer Discount",
    "discountValue": 25.0,
    "maxDiscountAmount": 75.0
  },
  "commandId": 124
}
```

### Delete Discount Rule

**DELETE** `/api/v1/products/{productId}/discount-rules/{ruleId}`

Deletes a discount rule.

**Parameters:**
- `productId` (path): The ID of the savings product
- `ruleId` (path): The ID of the discount rule

**Response:**
```json
{
  "resourceId": 2,
  "commandId": 125
}
```

### Preview Discount

**POST** `/api/v1/products/{productId}/discount-rules/preview`

Previews the impact of a discount rule without applying it.

**Request Body:**
```json
{
  "rule": {
    "discountType": "PERCENTAGE",
    "discountValue": 15.0,
    "maxDiscountAmount": 100.0,
    "conditions": {
      "accountBalance": {
        "enabled": true,
        "minimumBalance": 5000
      }
    }
  },
  "context": {
    "originalAmount": 200.0,
    "accountBalance": 15000,
    "clientType": "EXISTING"
  }
}
```

**Response:**
```json
{
  "discountAmount": 30.0,
  "finalAmount": 170.0,
  "discountPercentage": 15.0
}
```

## Analytics API

### Get Discount Analytics

**GET** `/api/v1/products/{productId}/discount-analytics`

Retrieves analytics data for discount applications.

**Parameters:**
- `productId` (path): The ID of the savings product
- `fromDate` (query): Start date (YYYY-MM-DD)
- `toDate` (query): End date (YYYY-MM-DD)

**Response:**
```json
{
  "fromDate": "2024-01-01",
  "toDate": "2024-01-31",
  "productId": 1,
  "totalApplications": 150,
  "totalOriginalAmount": 30000.0,
  "totalDiscountAmount": 4500.0,
  "totalFinalAmount": 25500.0,
  "averageDiscountPercentage": 15.0
}
```

### Get Discount Applications

**GET** `/api/v1/products/{productId}/discount-analytics/applications`

Retrieves detailed list of discount applications.

**Parameters:**
- `productId` (path): The ID of the savings product

**Response:**
```json
[
  {
    "id": 1,
    "discountRuleId": 1,
    "accountId": 100,
    "chargeId": 50,
    "originalAmount": 100.0,
    "discountAmount": 15.0,
    "finalAmount": 85.0,
    "applicationDate": "2024-01-15T10:30:00Z",
    "transactionId": 200
  }
]
```

### Get Total Discount Amount

**GET** `/api/v1/products/{productId}/discount-analytics/total-discount`

Retrieves total discount amount for a product.

**Parameters:**
- `productId` (path): The ID of the savings product

**Response:**
```json
{
  "totalDiscountAmount": 4500.0
}
```

## Data Models

### ProductDiscountRule

```typescript
interface ProductDiscountRule {
  id?: number;
  productId: number;
  productType: string;
  name: string;
  description?: string;
  discountType: 'PERCENTAGE' | 'FLAT';
  discountValue: number;
  maxDiscountAmount?: number;
  conditions: ProductDiscountConditions;
  validFrom?: string;
  validTo?: string;
  isActive: boolean;
  priority: number;
}
```

### ProductDiscountConditions

```typescript
interface ProductDiscountConditions {
  accountBalance?: AccountBalanceCondition;
  transaction?: TransactionCondition;
  timeBased?: TimeBasedCondition;
  client?: ClientCondition;
}

interface AccountBalanceCondition {
  enabled: boolean;
  minimumBalance?: number;
  maximumBalance?: number;
  balanceType: 'CURRENT' | 'AVAILABLE' | 'TOTAL';
}

interface TransactionCondition {
  enabled: boolean;
  minimumAmount?: number;
  maximumAmount?: number;
  transactionTypes?: string[];
  frequency?: 'FIRST_TIME' | 'REGULAR' | 'VIP';
}

interface TimeBasedCondition {
  enabled: boolean;
  validFrom?: string;
  validTo?: string;
  daysOfWeek?: number[];
  timeOfDay?: TimeRange;
}

interface ClientCondition {
  enabled: boolean;
  clientType?: 'NEW' | 'EXISTING' | 'VIP';
  minimumAccountAge?: number;
  maximumAccountAge?: number;
}
```

## Error Responses

### 400 Bad Request
```json
{
  "error": "Bad Request",
  "message": "Invalid request parameters",
  "details": [
    {
      "field": "discountValue",
      "message": "Discount value must be positive"
    }
  ]
}
```

### 401 Unauthorized
```json
{
  "error": "Unauthorized",
  "message": "Authentication required"
}
```

### 403 Forbidden
```json
{
  "error": "Forbidden",
  "message": "Insufficient permissions"
}
```

### 404 Not Found
```json
{
  "error": "Not Found",
  "message": "Discount rule not found"
}
```

### 500 Internal Server Error
```json
{
  "error": "Internal Server Error",
  "message": "An unexpected error occurred"
}
```

## Rate Limiting

API requests are rate limited to:
- 1000 requests per hour per user
- 100 requests per minute per user

Rate limit headers are included in responses:
```
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 999
X-RateLimit-Reset: 1640995200
```

## Examples

### Creating a High Balance Discount Rule

```bash
curl -X POST \
  'https://api.example.com/api/v1/products/1/discount-rules' \
  -H 'Authorization: Bearer <token>' \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "High Balance Discount",
    "description": "10% discount for accounts with balance > $10,000",
    "discountType": "PERCENTAGE",
    "discountValue": 10.0,
    "maxDiscountAmount": 100.0,
    "conditions": {
      "accountBalance": {
        "enabled": true,
        "minimumBalance": 10000,
        "balanceType": "CURRENT"
      }
    },
    "validFrom": "2024-01-01",
    "validTo": "2024-12-31",
    "priority": 1
  }'
```

### Getting Analytics for January 2024

```bash
curl -X GET \
  'https://api.example.com/api/v1/products/1/discount-analytics?fromDate=2024-01-01&toDate=2024-01-31' \
  -H 'Authorization: Bearer <token>'
```

## Support

For API support and questions, contact the development team or refer to the internal documentation.
