# Global Payments PHP Pay by Link Implementation

A comprehensive PHP implementation for creating and managing payment links using the Global Payments API. This application allows merchants to generate secure payment links that can be shared with customers via email, SMS, or other channels.

## Features

- **Pay by Link Creation**: Generate secure, customizable payment links
- **Multi-Currency Support**: Support for EUR, USD, GBP, and CAD
- **Flexible Configuration**: Environment-based configuration for sandbox/production
- **Comprehensive Logging**: Built-in request logging for debugging and monitoring
- **Modern PHP**: PHP 8.0+ with strict typing and PSR-4 autoloading
- **Docker Support**: Containerized deployment with multi-stage builds
- **Security Best Practices**: Input validation, sanitization, and error handling

## Requirements

- **PHP**: 8.0 or later
- **Extensions**: curl, json, zip, dom, intl
- **Composer**: For dependency management
- **Global Payments Account**: With Pay by Link API credentials

## Project Structure

```
php/
├── config.php                 # Configuration endpoint
├── create-payment-link.php     # Payment link creation endpoint
├── index.html                  # Payment link creation form
├── composer.json              # Dependencies and PSR-4 autoloading
├── .env.sample               # Environment configuration template
├── Dockerfile                # Container configuration
├── run.sh                    # Development server startup script
├── logs/                     # Application logs directory
└── vendor/                   # Composer dependencies
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
```

### 2. Installation

Install dependencies using Composer:

```bash
composer install
```

### 3. Running the Application

#### Using the convenience script:
```bash
chmod +x run.sh
./run.sh
```

#### Manual startup:
```bash
php -S localhost:8000
```

#### Using Docker:
```bash
docker build -t gp-pay-by-link .
docker run -p 8000:8000 --env-file .env gp-pay-by-link
```

### 4. Access the Application

Open your browser and navigate to:
- **Payment Form**: http://localhost:8000
- **Configuration API**: http://localhost:8000/config.php

## API Endpoints

### GET /config.php
Returns configuration information for the Pay by Link interface.

**Response**:
```json
{
  "success": true,
  "data": {
    "environment": "sandbox",
    "supportedCurrencies": ["EUR", "USD", "GBP", "CAD"],
    "supportedPaymentMethods": ["CARD"],
    "defaultCurrency": "EUR",
    "maxAmount": 999999,
    "minAmount": 1,
    "usageModes": {
      "SINGLE": "Single Use",
      "MULTIPLE": "Multiple Use"
    },
    "api": {
      "version": "2021-03-22",
      "baseUrl": "https://apis.sandbox.globalpay.com"
    }
  }
}
```

### POST /create-payment-link.php
Creates a new payment link with the specified parameters.

**Request Parameters**:
- `amount` (integer, required) - Amount in cents (e.g., 1000 = €10.00)
- `currency` (string, required) - Currency code (EUR, USD, GBP, CAD)
- `reference` (string, required) - Payment reference (max 100 chars)
- `name` (string, required) - Payment name/title (max 100 chars)
- `description` (string, required) - Payment description (max 500 chars)

**Success Response**:
```json
{
  "success": true,
  "message": "Payment link created successfully! Link ID: lnk_xxx",
  "data": {
    "paymentLink": "https://pay.sandbox.globalpay.com/lnk_xxx",
    "linkId": "lnk_xxx",
    "reference": "Invoice #1234567",
    "amount": 1000,
    "currency": "EUR"
  }
}
```

**Error Response**:
```json
{
  "success": false,
  "message": "Payment link creation failed",
  "error": {
    "code": "API_ERROR",
    "details": "Detailed error message"
  }
}
```

## Implementation Details

### SDK Configuration

The application uses the Global Payments PHP SDK with the following configuration:

```php
$config = new GpApiConfig();
$config->appId = $_ENV['GP_API_APP_ID'];
$config->appKey = $_ENV['GP_API_APP_KEY'];
$config->environment = Environment::TEST; // or Environment::PRODUCTION
$config->channel = Channel::CardNotPresent;
$config->country = 'GB';

// Pay by Link specific configuration
$accessTokenInfo = new AccessTokenInfo();
$accessTokenInfo->transactionProcessingAccountName = 'paylink';
$config->accessTokenInfo = $accessTokenInfo;
```

### Payment Link Configuration

Payment links are created with the following default settings:

- **Type**: PAYMENT
- **Usage Mode**: SINGLE (one-time use)
- **Allowed Payment Methods**: CARD
- **Usage Limit**: 1
- **Expiration**: 10 days from creation
- **Shipping**: Enabled with €0 shipping amount

### Security Features

- **Input Sanitization**: All user inputs are sanitized and validated
- **Reference Sanitization**: Removes potentially harmful characters
- **Length Limits**: Enforced on all text fields
- **Amount Validation**: Ensures positive integer amounts
- **CORS Headers**: Properly configured for API access
- **Error Handling**: Comprehensive exception handling

### Logging

The application includes comprehensive logging via the Global Payments SDK:

```php
$config->requestLogger = new SampleRequestLogger(new Logger("logs"));
```

Logs are stored in the `logs/` directory and include:
- API request/response details
- Payment link creation events
- Error conditions and exceptions

## Customization

### Adding Payment Methods

To support additional payment methods, update the `allowedPaymentMethods` array:

```php
$payByLink->allowedPaymentMethods = [
    PaymentMethodName::CARD,
    PaymentMethodName::APM  // Alternative payment methods
];
```

### Modifying Link Expiration

Change the expiration period by updating:

```php
$payByLink->expirationDate = date('Y-m-d H:i:s', strtotime('+30 days'));
```

### Custom Return URLs

Configure custom URLs for payment completion:

```php
$payByLink->returnUrl = 'https://your-domain.com/success';
$payByLink->cancelUrl = 'https://your-domain.com/cancel';
$payByLink->statusUpdateUrl = 'https://your-domain.com/webhook';
```

## Production Deployment

### Environment Configuration

1. Update environment variables for production:
   ```env
   GP_API_ENVIRONMENT=production
   GP_API_APP_ID=your_production_app_id
   GP_API_APP_KEY=your_production_app_key
   ```

2. Use production API endpoints in the SDK configuration:
   ```php
   $config->environment = Environment::PRODUCTION;
   ```

### Security Enhancements

For production deployment, implement:

- **HTTPS**: Always use SSL/TLS encryption
- **Input Validation**: Enhanced validation beyond basic sanitization
- **Rate Limiting**: Prevent API abuse
- **Request Logging**: Monitor all payment link creation requests
- **Error Monitoring**: Set up alerts for API failures
- **IP Whitelisting**: Restrict API access to known IP ranges
- **CSRF Protection**: Implement CSRF tokens for form submissions
- **Content Security Policy**: Add appropriate CSP headers

### Docker Deployment

The included Dockerfile provides a production-ready container:

```dockerfile
FROM php:8.3-cli
# Optimized for production with minimal attack surface
# Non-root user execution
# Composer optimizations for production
```

Build and deploy:

```bash
docker build -t gp-pay-by-link:production .
docker run -d \
  -p 80:8000 \
  --env-file .env.production \
  --name pay-by-link \
  gp-pay-by-link:production
```

## Troubleshooting

### Common Issues

1. **Missing Environment Variables**: Ensure `.env` file is properly configured
2. **PHP Extensions**: Verify required extensions are installed
3. **File Permissions**: Ensure `logs/` directory is writable
4. **API Credentials**: Verify credentials are valid for the target environment

### Debug Mode

Enable detailed error reporting for development:

```php
ini_set('display_errors', '1');
error_reporting(E_ALL);
```

### Log Analysis

Check application logs in the `logs/` directory for:
- API request/response details
- Payment link creation events
- Error conditions and stack traces

## Support

- **Documentation**: [Global Payments Developer Portal](https://developer.globalpay.com/)
- **API Reference**: [Global Payments API Documentation](https://developer.globalpay.com/api)
- **SDK Documentation**: [PHP SDK Repository](https://github.com/globalpayments/php-sdk)
