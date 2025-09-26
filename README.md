# Global Payments Pay by Link - Multi-Language Implementation

This repository provides comprehensive **Pay by Link** implementations using the Global Payments GP API across six programming languages. Pay by Link allows merchants to create secure payment links that can be shared with customers via email, SMS, or other channels, enabling remote payments without requiring customers to visit a physical location or website.

## Available Implementations

- **[.NET Core](./dotnet/)** - ASP.NET Core web application with minimal APIs
- **[Go](./go/)** - Native Go HTTP server with direct API integration
- **[Java](./java/)** - Jakarta EE servlet-based web application
- **[Node.js](./nodejs/)** - Express.js web application with GP SDK integration
- **[PHP](./php/)** - Native PHP implementation with GP SDK
- **[Python](./python/)** - Flask web application with direct API calls

## Features

### Core Pay by Link Functionality

- **Payment Link Creation** - Generate secure, time-limited payment links
- **Multi-Currency Support** - EUR, USD, GBP currency support
- **Input Validation** - Comprehensive validation and sanitization
- **Error Handling** - Standardized error responses across all languages
- **GP API Integration** - Direct integration with Global Payments sandbox/production APIs

### Implementation Features

- **Consistent API** - Identical `/create-payment-link` endpoint across all languages
- **Environment Configuration** - Secure credential management with `.env` files
- **Responsive Frontend** - HTML forms with real-time validation
- **Production Ready** - Comprehensive error handling and logging
- **OWASP Compliant** - Input sanitization and security best practices

## Quick Start

### 1. Choose Your Language

Navigate to any implementation directory:

```bash
cd nodejs/    # Node.js with Express
cd python/    # Python with Flask
cd php/       # Native PHP
cd java/      # Java with Jakarta EE
cd dotnet/    # .NET Core
cd go/        # Go HTTP server
```

### 2. Set Up Environment

Copy the sample environment file and add your GP API credentials:

```bash
cp .env.sample .env
```

Edit `.env` with your actual GP API credentials:

```env
# GP API App Credentials (required for Pay by Link)
GP_API_APP_ID=your_app_id_here
GP_API_APP_KEY=your_app_key_here

# Environment (sandbox or production)
GP_API_ENVIRONMENT=sandbox
```

### 3. Install Dependencies

**Node.js:**
```bash
npm install
```

**Python:**
```bash
pip install -r requirements.txt
```

**PHP:**
```bash
composer install
```

**Java:**
```bash
mvn clean install
```

**Go:**
```bash
go mod download
```

**.NET:**
```bash
dotnet restore
```

### 4. Run the Server

**Node.js:**
```bash
npm start
# or
node server.js
```

**Python:**
```bash
python server.py
```

**PHP:**
```bash
php -S localhost:8000 -t .
```

**Java:**
```bash
mvn cargo:run
```

**Go:**
```bash
go run main.go
```

**.NET:**
```bash
dotnet run
```

### 5. Test the Implementation

Visit `http://localhost:8000` and create a test payment link:

1. Enter amount in cents (e.g., 1000 = $10.00)
2. Select currency (EUR, USD, GBP)
3. Provide reference, name, and description
4. Click "Create Payment Link"
5. Copy the generated payment link to share with customers

## API Endpoints

### GET `/config`

Returns configuration data for client-side use:

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

### POST `/create-payment-link`

Creates a new payment link. **Required fields:**

- `amount` (integer) - Amount in cents
- `currency` (string) - Currency code (EUR/USD/GBP)
- `reference` (string) - Merchant reference
- `name` (string) - Payment name/title
- `description` (string) - Payment description

**Success Response:**
```json
{
  "success": true,
  "message": "Payment link created successfully! Link ID: lnk_xxx",
  "data": {
    "paymentLink": "https://pay.sandbox.globalpay.com/link/lnk_xxx",
    "linkId": "lnk_xxx",
    "reference": "ORDER-123",
    "amount": 1000,
    "currency": "USD"
  }
}
```

**Error Response:**
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

## Payment Link Configuration

All implementations create payment links with these settings:

- **Type:** PAYMENT (single-use payment)
- **Usage Mode:** SINGLE (one-time use)
- **Usage Limit:** 1
- **Expiration:** 10 days from creation
- **Channel:** CNP (Card Not Present)
- **Country:** GB (Great Britain)
- **Shipping:** YES with 0 amount
- **Payment Methods:** CARD only
- **Notifications:**
  - Return URL: `https://www.example.com/returnUrl`
  - Status URL: `https://www.example.com/statusUrl`
  - Cancel URL: `https://www.example.com/returnUrl`

## Input Validation & Security

### Reference Sanitization

All implementations sanitize the reference field using regex pattern `[^\w\s\-#]` to remove potentially harmful characters, allowing only:
- Word characters (letters, digits, underscore)
- Spaces, hyphens, and hash symbols
- Limited to 100 characters maximum

### Field Validation

- **Amount:** Must be positive integer (cents)
- **Currency:** Must be one of EUR, USD, GBP
- **Name:** Trimmed and limited to 100 characters
- **Description:** Trimmed and limited to 500 characters
- **Reference:** Sanitized and limited to 100 characters

## Error Handling

Standardized error codes across all implementations:

- `MISSING_REQUIRED_FIELDS` - Required fields missing from request
- `INVALID_AMOUNT` - Amount is not a positive integer
- `TOKEN_GENERATION_ERROR` - Failed to generate GP API access token
- `API_ERROR` - GP API request failed
- `INVALID_RESPONSE` - Unexpected response format from GP API
- `UNKNOWN_ERROR` - Unexpected system error

## Prerequisites

- **Global Payments Account** with GP API credentials
- **Development Environment** for your chosen language
- **Package Manager** (npm, pip, composer, maven, dotnet, go)
- **GP API Credentials:**
  - App ID (`GP_API_APP_ID`)
  - App Key (`GP_API_APP_KEY`)

## Production Deployment

### Environment Variables

Set production environment variables:

```env
GP_API_APP_ID=your_production_app_id
GP_API_APP_KEY=your_production_app_key
GP_API_ENVIRONMENT=production
```

### Security Considerations

- **Use HTTPS** for all production deployments
- **Validate inputs** on both client and server side
- **Log transactions** for audit and debugging
- **Monitor failed requests** for potential issues
- **Rate limit** the API endpoints
- **Secure credential storage** using environment variables or secrets management

### URL Configuration

Update notification URLs in production:

```javascript
notifications: {
  return_url: "https://yourdomain.com/payment-return",
  status_url: "https://yourdomain.com/payment-webhook",
  cancel_url: "https://yourdomain.com/payment-cancel"
}
```

## Architecture Notes

### Direct API vs SDK

- **Node.js & PHP:** Use GP SDK for authentication, direct API calls for payment links
- **Python, Go, Java, .NET:** Use direct HTTP API calls for both authentication and payment links
- **Consistency:** All implementations achieve identical functionality regardless of approach

### Response Format

All implementations return identical JSON response formats to ensure consistent client-side handling across different backend languages.

### Error Handling Strategy

Each implementation includes try-catch blocks with specific error categorization, ensuring consistent error reporting and debugging capabilities.

## Support

For questions about this implementation or Global Payments integration:

1. **Global Payments Documentation:** [developer.globalpay.com](https://developer.globalpay.com)
2. **GP API Reference:** [GP API Documentation](https://developer.globalpay.com/api)
3. **Technical Support:** Contact your Global Payments integration specialist

## License

This project is licensed under the MIT License - see individual implementation directories for specific license details.
