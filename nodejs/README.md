# Global Payments Node.js Pay by Link Implementation

A comprehensive Node.js implementation for creating and managing payment links using the Global Payments API. This Express.js application provides a modern JavaScript/TypeScript-friendly approach to generating secure payment links that can be shared with customers via email, SMS, or other channels.

## Features

- **Express.js Framework**: Modern Node.js web framework with middleware support
- **ES Modules**: Uses ESM (`"type": "module"`) for modern JavaScript standards
- **Hybrid SDK Approach**: Combines Global Payments SDK for authentication with direct API calls for payment links
- **Multi-Currency Support**: Support for EUR, USD, GBP, and other currencies
- **Environment Configuration**: Flexible .env-based configuration for sandbox/production
- **Input Validation**: Comprehensive request validation and sanitization
- **Error Handling**: Robust error handling with detailed error codes
- **Static File Serving**: Built-in static file serving for frontend assets
- **JSON & Form Support**: Handles both JSON and form-encoded requests

## Requirements

- **Node.js**: 16.0 or later (ESM support required)
- **npm**: 8.0 or later
- **Global Payments Account**: With Pay by Link API credentials

## Project Structure

```
nodejs/
├── server.js                  # Main Express server and API endpoints
├── index.html                 # Payment link creation form
├── package.json              # Dependencies and scripts
├── .env.sample               # Environment configuration template
└── node_modules/             # npm dependencies
```

## Quick Start

### 1. Environment Setup

Copy the environment template and configure your credentials:

```bash
cp .env.sample .env
```

Update `.env` with your Global Payments API credentials:

```env
# GP API App Credentials (required for Pay by Link)
GP_API_APP_ID=your_app_id_here
GP_API_APP_KEY=your_app_key_here

# Environment (sandbox or production)
GP_API_ENVIRONMENT=sandbox

# Server configuration
PORT=8000
```

### 2. Installation

Install dependencies using npm:

```bash
npm install
```

### 3. Running the Application

Start the development server:

```bash
npm start
```

Or run directly with Node.js:

```bash
node server.js
```

### 4. Access the Application

Open your browser and navigate to:
- **Payment Form**: http://localhost:8000
- **Configuration API**: http://localhost:8000/config

## API Endpoints

### GET /config

Returns configuration information for the Pay by Link interface.

**Response**:
```json
{
  "success": true,
  "data": {
    "environment": "sandbox",
    "supportedCurrencies": ["EUR", "USD", "GBP"],
    "supportedPaymentMethods": ["CARD"]
  }
}
```

### POST /create-payment-link

Creates a new payment link with the specified parameters.

**Request Headers**:
```
Content-Type: application/json
```

**Request Parameters**:
- `amount` (integer, required) - Amount in cents (e.g., 1000 = $10.00)
- `currency` (string, required) - Currency code (EUR, USD, GBP)
- `reference` (string, required) - Payment reference (max 100 chars)
- `name` (string, required) - Payment name/title (max 100 chars)
- `description` (string, required) - Payment description (max 500 chars)

**Example Request**:
```bash
curl -X POST http://localhost:8000/create-payment-link \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 2500,
    "currency": "USD",
    "reference": "Invoice #12345",
    "name": "Product Purchase",
    "description": "Payment for premium subscription"
  }'
```

**Success Response**:
```json
{
  "success": true,
  "message": "Payment link created successfully! Link ID: lnk_xxx",
  "data": {
    "paymentLink": "https://pay.sandbox.globalpay.com/lnk_xxx",
    "linkId": "lnk_xxx",
    "reference": "Invoice #12345",
    "amount": 2500,
    "currency": "USD"
  }
}
```

**Error Responses**:

Missing Required Fields (400):
```json
{
  "success": false,
  "message": "Payment link creation failed",
  "error": {
    "code": "MISSING_REQUIRED_FIELDS",
    "details": "Missing required fields. Received: amount, currency"
  }
}
```

Invalid Amount (400):
```json
{
  "success": false,
  "message": "Payment link creation failed",
  "error": {
    "code": "INVALID_AMOUNT",
    "details": "Invalid amount"
  }
}
```

API Error (500):
```json
{
  "success": false,
  "message": "Payment link creation failed",
  "error": {
    "code": "API_ERROR",
    "details": "Detailed error message from API"
  }
}
```

## Code Structure

### Main Components

#### Express Application Setup
```javascript
import express from 'express';
import * as dotenv from 'dotenv';

const app = express();
const port = process.env.PORT || 8000;

// Middleware configuration
app.use(express.static('.'));           // Static file serving
app.use(express.urlencoded({ extended: true })); // Form data parsing
app.use(express.json());                // JSON request parsing
```

#### Global Payments SDK Configuration
```javascript
import { ServicesContainer, GpApiConfig, Environment, Channel } from 'globalpayments-api';

const config = new GpApiConfig();
config.appId = process.env.GP_API_APP_ID;
config.appKey = process.env.GP_API_APP_KEY;
config.environment = Environment.Test; // or Environment.Production
config.channel = Channel.CardNotPresent;
config.country = 'GB';

// Pay by Link specific configuration
config.accessTokenInfo = {
    transactionProcessingAccountName: 'paylink'
};

ServicesContainer.configureService(config);
```

#### Hybrid SDK + Direct API Approach
The implementation uses a hybrid approach:

1. **SDK for Authentication**: Uses Global Payments SDK to generate access tokens
2. **Direct API for Payment Links**: Makes direct HTTP requests to create payment links

```javascript
// Generate token using SDK
const tokenResponse = await GpApiService.generateTransactionKey(config);
const accessToken = tokenResponse.accessToken;

// Create payment link via direct API call
const response = await fetch("https://apis.sandbox.globalpay.com/ucp/links", {
    method: "POST",
    headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${accessToken}`,
        "X-GP-Version": "2021-03-22",
    },
    body: JSON.stringify(payByLinkData)
});
```

### Input Validation and Sanitization

#### Reference Sanitization
```javascript
const sanitizeReference = (reference) => {
    if (!reference) return '';
    // Remove non-alphanumeric characters except spaces, hyphens, and hash
    const sanitized = reference.replace(/[^\w\s\-#]/g, '');
    return sanitized.substring(0, 100);
};
```

#### Required Field Validation
```javascript
const requiredFields = ['amount', 'currency', 'reference', 'name', 'description'];
const missingFields = requiredFields.filter(field => !req.body[field]);

if (missingFields.length > 0) {
    return res.status(400).json({
        success: false,
        message: 'Payment link creation failed',
        error: {
            code: 'MISSING_REQUIRED_FIELDS',
            details: `Missing required fields. Received: ${Object.keys(req.body).join(', ')}`
        }
    });
}
```

## Dependencies

### Core Dependencies

- **express** (^4.18.2): Fast, unopinionated web framework for Node.js
- **dotenv** (^16.3.1): Environment variable management
- **globalpayments-api** (^3.10.6): Official Global Payments SDK for Node.js

### Package Configuration

The application uses ES modules (`"type": "module"`) for modern JavaScript syntax:

```json
{
  "name": "pay-by-link-nodejs",
  "version": "1.0.0",
  "type": "module",
  "main": "server.js",
  "scripts": {
    "start": "node server.js"
  }
}
```

## Implementation Details

### Payment Link Configuration

Payment links are created with the following settings:

- **Type**: PAYMENT
- **Usage Mode**: SINGLE (one-time use)
- **Usage Limit**: 1
- **Allowed Payment Methods**: CARD
- **Channel**: CNP (Card Not Present)
- **Expiration**: 10 days from creation
- **Shipping**: Enabled with $0 shipping amount

### Default URLs Configuration
```javascript
const payByLinkData = {
    // ... other configuration
    notifications: {
        return_url: "https://www.example.com/returnUrl",
        status_url: "https://www.example.com/statusUrl",
        cancel_url: "https://www.example.com/returnUrl"
    }
};
```

### Error Handling

The application implements comprehensive error handling with specific error codes:

- `MISSING_REQUIRED_FIELDS`: Required parameters not provided
- `INVALID_AMOUNT`: Amount is not a positive integer
- `API_ERROR`: Error response from Global Payments API
- `TOKEN_GENERATION_ERROR`: Failed to generate access token
- `INVALID_RESPONSE`: API response missing expected data
- `UNKNOWN_ERROR`: Unexpected errors

## Development vs Production

### Development Configuration

```javascript
// Development environment
config.environment = Environment.Test;

// Uses sandbox API endpoints
const response = await fetch("https://apis.sandbox.globalpay.com/ucp/links", {
    // ... request configuration
});
```

### Production Configuration

For production deployment:

1. Update environment variables:
   ```env
   GP_API_ENVIRONMENT=production
   GP_API_APP_ID=your_production_app_id
   GP_API_APP_KEY=your_production_app_key
   ```

2. Update API configuration:
   ```javascript
   config.environment = Environment.Production;
   ```

3. Update API endpoints:
   ```javascript
   const response = await fetch("https://apis.globalpay.com/ucp/links", {
       // ... request configuration
   });
   ```

## Security Features

- **Input Sanitization**: All user inputs are sanitized and validated
- **Reference Sanitization**: Removes potentially harmful characters
- **Length Limits**: Enforced on all text fields (reference: 100 chars, name: 100 chars, description: 500 chars)
- **Amount Validation**: Ensures positive integer amounts only
- **Environment Isolation**: Clear separation between sandbox and production
- **Token Security**: Access tokens are generated fresh for each request
- **Error Information**: Error responses don't expose sensitive internal details

## Troubleshooting

### Common Issues

1. **Module Import Errors**
   ```
   Error: Cannot use import statement outside a module
   ```
   Solution: Ensure `"type": "module"` is set in package.json

2. **Missing Environment Variables**
   ```
   Payment link creation failed: Failed to generate access token
   ```
   Solution: Verify `.env` file exists and contains valid GP_API_APP_ID and GP_API_APP_KEY

3. **Port Already in Use**
   ```
   Error: listen EADDRINUSE :::8000
   ```
   Solution: Change the port by setting `PORT` environment variable or kill the process using port 8000

4. **SDK Configuration Issues**
   ```
   Error: Invalid credentials
   ```
   Solution: Verify API credentials are correct for the target environment (sandbox vs production)

### Debug Mode

Enable detailed console logging by examining the server output:

```bash
node server.js
# Server will output:
# - Received POST data
# - Payment link creation attempts
# - API request details
# - Error conditions
```

### Testing the API

Test the endpoints using curl or Postman:

```bash
# Test configuration endpoint
curl http://localhost:8000/config

# Test payment link creation
curl -X POST http://localhost:8000/create-payment-link \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 1000,
    "currency": "USD",
    "reference": "Test Payment",
    "name": "Test Product",
    "description": "Testing payment link creation"
  }'
```

## Customization

### Adding Payment Methods

To support additional payment methods, update the allowed payment methods:

```javascript
const payByLinkData = {
    // ... other configuration
    transactions: {
        allowed_payment_methods: ["CARD", "APM"], // Add alternative payment methods
        // ... other transaction settings
    }
};
```

### Custom Return URLs

Configure custom URLs for your application:

```javascript
const payByLinkData = {
    // ... other configuration
    notifications: {
        return_url: "https://yourdomain.com/payment/success",
        status_url: "https://yourdomain.com/webhook/payment-status",
        cancel_url: "https://yourdomain.com/payment/cancel"
    }
};
```

### Modifying Link Expiration

Change the expiration period:

```javascript
const payByLinkData = {
    // ... other configuration
    expiration_date: new Date(Date.now() + (30 * 24 * 60 * 60 * 1000)).toISOString(), // 30 days
};
```

### Adding Request Logging

Implement custom request logging:

```javascript
app.use((req, res, next) => {
    console.log(`${new Date().toISOString()} - ${req.method} ${req.path}`);
    next();
});
```

## Support

- **Documentation**: [Global Payments Developer Portal](https://developer.globalpay.com/)
- **API Reference**: [Global Payments API Documentation](https://developer.globalpay.com/api)
- **Node.js SDK**: [Node.js SDK Repository](https://github.com/globalpayments/node-sdk)
- **Express.js Documentation**: [Express.js Guide](https://expressjs.com/)
