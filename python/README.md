# Global Payments Python Pay by Link Implementation

A modern Flask 3.0 implementation for creating and managing payment links using the Global Payments API. This lightweight Python application provides high-performance payment link generation with direct HTTP API integration, comprehensive validation, and production-ready error handling using Flask's minimalist web framework.

## Features

- **Flask 3.0**: Built on the latest Flask framework with minimal overhead and maximum flexibility
- **Direct HTTP Integration**: Uses requests library for direct GP API communication without SDK dependencies
- **Environment Configuration**: python-dotenv support for flexible deployment and configuration management
- **Input Validation & Sanitization**: Comprehensive request validation with Python regex patterns and type hints
- **Multi-Currency Support**: Support for EUR, USD, GBP, and other Global Payments currencies
- **Type Hints**: Enhanced code clarity and IDE support with Python type annotations throughout
- **Static File Serving**: Built-in static content serving with Flask's send_static_file()
- **Cross-Platform Deployment**: Runs on any platform supporting Python 3.8+ (Windows, Linux, macOS, containers)
- **Production-Ready Error Handling**: Structured error responses with proper HTTP status codes
- **WSGI Compatible**: Production deployment ready with gunicorn WSGI server included

## Requirements

- **Python 3.8+**: Modern Python runtime with type hints and async support
- **pip**: Python package manager for dependency installation
- **Virtual Environment**: Recommended for isolated dependency management
- **Global Payments Account**: With Pay by Link API credentials (GP API App ID and Key)

## Project Structure

```
python/
├── server.py                    # Main Flask application with API endpoints
├── requirements.txt             # Python dependencies and version specifications
├── index.html                   # Payment form interface
├── .env.sample                  # Environment configuration template
├── gunicorn.conf.py            # Production WSGI server configuration (optional)
└── logs/                       # Application logs directory (auto-created)
```

## Quick Start

### 1. Environment Setup

Create and activate a Python virtual environment:

```bash
# Create virtual environment
python -m venv venv

# Activate virtual environment
# On Linux/macOS:
source venv/bin/activate
# On Windows:
venv\Scripts\activate

# Verify Python version (should be 3.8+)
python --version
```

Copy the environment template and configure your credentials:

```bash
cp .env.sample .env
```

Update `.env` with your Global Payments API credentials:

```env
# Global Payments API Configuration for Pay by Link
# Replace these sample values with your actual GP API credentials

# GP API App Credentials (required for Pay by Link)
GP_API_APP_ID=your_app_id_here
GP_API_APP_KEY=your_app_key_here

# Environment (sandbox or production)
GP_API_ENVIRONMENT=sandbox

# Server Configuration (optional)
PORT=8000
FLASK_ENV=development
```

### 2. Dependencies Installation

Install Python dependencies using pip:

```bash
# Install all dependencies
pip install -r requirements.txt

# Or install individually
pip install Flask==3.0.0 requests==2.31.0 python-dotenv==1.0.0 gunicorn==21.2.0

# Verify installation
pip list | grep -E "(Flask|requests|python-dotenv|gunicorn)"
```

### 3. Running the Application

#### Development Mode

Run the Flask application locally with the built-in development server:

```bash
# Start the development server (default port 8000)
python server.py

# The server will be available at http://localhost:8000
# Flask development server includes hot reload and debugging
```

#### Custom Port Configuration

```bash
# Run on custom port using environment variable
PORT=5000 python server.py

# Or set in .env file
echo "PORT=5000" >> .env
python server.py

# Using Flask's built-in options
FLASK_RUN_PORT=5000 flask --app server run --host=0.0.0.0
```

#### Production Mode with Gunicorn

Run with production WSGI server for better performance:

```bash
# Basic gunicorn deployment
gunicorn --bind 0.0.0.0:8000 --workers 4 server:app

# With custom configuration
gunicorn --config gunicorn.conf.py server:app

# Background deployment with process management
gunicorn --bind 0.0.0.0:8000 --workers 4 --daemon --pid gunicorn.pid server:app

# Check gunicorn processes
ps aux | grep gunicorn
```

### 4. Access the Application

Open your browser and navigate to:
- **Server Root**: http://localhost:8000
- **Payment Form**: http://localhost:8000/index.html
- **Configuration API**: http://localhost:8000/config

## API Endpoints

### GET /config

Returns configuration information for the Pay by Link interface, including supported currencies and payment methods.

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

Creates a new payment link with the specified parameters using direct GP API integration.

**Request Headers**:
```
Content-Type: application/x-www-form-urlencoded
```

**Request Parameters**:
- `amount` (string, required) - Amount in cents (e.g., "1000" = $10.00)
- `currency` (string, required) - Currency code (EUR, USD, GBP)
- `reference` (string, required) - Payment reference (max 100 chars, sanitized)
- `name` (string, required) - Payment name/title (max 100 chars)
- `description` (string, required) - Payment description (max 500 chars)

**Example cURL Request**:
```bash
curl -X POST http://localhost:8000/create-payment-link \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d 'amount=2500&currency=USD&reference=Invoice%20%2312345&name=Product%20Purchase&description=Payment%20for%20premium%20subscription'
```

**Example Python Requests**:
```python
import requests

# Test payment link creation
response = requests.post('http://localhost:8000/create-payment-link', data={
    'amount': '2500',
    'currency': 'USD',
    'reference': 'Invoice #12345',
    'name': 'Product Purchase',
    'description': 'Payment for premium subscription'
})

print(response.json())
```

**Success Response (200)**:
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
    "details": "Missing required fields. Received: amount, currency, reference, name, description"
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

Token Generation Error (400):
```json
{
  "success": false,
  "message": "Payment link creation failed",
  "error": {
    "code": "TOKEN_GENERATION_ERROR",
    "details": "Failed to generate access token"
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
    "details": "Detailed error message from GP API"
  }
}
```

## Code Structure

### Flask Application Architecture

The `server.py` file contains the complete Flask application with modular function organization:

```python
"""
Global Payments Pay by Link - Python Flask

This Flask application provides Pay by Link functionality using the Global Payments API.
Creates secure payment links that can be shared with customers via email, SMS, or other channels.
"""

import os
import re
import json
import requests
from datetime import datetime, timedelta
from flask import Flask, request, jsonify
from dotenv import load_dotenv

# Load environment variables
load_dotenv()

# Initialize application
app = Flask(__name__, static_folder='.')

# GP API Configuration
GP_API_APP_ID = os.getenv('GP_API_APP_ID', 'QgKeFv7BuZlDcZvUeaxdA2jRsFukThCD')
GP_API_APP_KEY = os.getenv('GP_API_APP_KEY', 'eS5f8cCbuK8c6d5T')
GP_API_BASE_URL = 'https://apis.sandbox.globalpay.com/ucp'
GP_API_VERSION = '2021-03-22'
```

### Core Functions

**Reference Sanitization** (matching other implementations):
```python
def sanitize_reference(reference: str) -> str:
    """
    Sanitize reference string by removing potentially harmful characters.
    Matches the regex pattern from other implementations: [^\\w\\s\\-#]

    Args:
        reference: The reference to sanitize

    Returns:
        Sanitized reference containing only safe characters, limited to 100 characters
    """
    if not reference:
        return ''

    # Remove non-alphanumeric characters except spaces, hyphens, and hash
    sanitized = re.sub(r'[^\w\s\-#]', '', reference)
    return sanitized[:100]
```

**Access Token Generation**:
```python
def generate_access_token():
    """
    Generate an access token for GP API using app credentials.

    Returns:
        dict: Token response containing access token and account info

    Raises:
        Exception: If token generation fails
    """
    if not GP_API_APP_ID or not GP_API_APP_KEY:
        raise Exception('Missing GP_API_APP_ID or GP_API_APP_KEY environment variables')

    token_request = {
        'app_id': GP_API_APP_ID,
        'app_key': GP_API_APP_KEY,
        'permissions': ['PMT_POST_Create', 'PMT_POST_Detokenize']
    }

    headers = {
        'Content-Type': 'application/json',
        'X-GP-Version': GP_API_VERSION
    }

    response = requests.post(
        f'{GP_API_BASE_URL}/accesstoken',
        json=token_request,
        headers=headers
    )

    if not response.ok:
        raise Exception(f'Token request failed with status {response.status_code}: {response.text}')

    return response.json()
```

### Flask Route Handlers

**Configuration Endpoint**:
```python
@app.route('/config')
def get_config():
    """
    Config endpoint - provides configuration for client-side use.
    Returns standardized response matching other implementations.
    """
    return jsonify({
        'success': True,
        'data': {
            'environment': 'sandbox',
            'supportedCurrencies': ['EUR', 'USD', 'GBP'],
            'supportedPaymentMethods': ['CARD']
        }
    })
```

**Payment Link Creation Endpoint**:
```python
@app.route('/create-payment-link', methods=['POST'])
def create_payment_link():
    """
    Create payment link endpoint.
    Creates a new payment link using Global Payments API with direct HTTP calls.
    Replicates functionality from other language implementations exactly.
    """
    try:
        print(f'Received POST data: {dict(request.form)}')

        # Validate required fields - exact match with other implementations
        required_fields = ['amount', 'currency', 'reference', 'name', 'description']
        missing_fields = [field for field in required_fields if not request.form.get(field)]

        if missing_fields:
            return jsonify({
                'success': False,
                'message': 'Payment link creation failed',
                'error': {
                    'code': 'MISSING_REQUIRED_FIELDS',
                    'details': f'Missing required fields. Received: {", ".join(request.form.keys())}'
                }
            }), 400

        # Parse and validate amount
        try:
            amount = int(request.form['amount'])
            if amount <= 0:
                raise ValueError('Amount must be positive')
        except (ValueError, TypeError):
            return jsonify({
                'success': False,
                'message': 'Payment link creation failed',
                'error': {
                    'code': 'INVALID_AMOUNT',
                    'details': 'Invalid amount'
                }
            }), 400

        # Additional processing logic...

    except Exception as error:
        print(f'Payment link creation failed: {error}')
        # Error handling logic...
```

### Form Data Processing

Flask request form handling with comprehensive validation:

```python
# Extract and validate form data
required_fields = ['amount', 'currency', 'reference', 'name', 'description']
missing_fields = [field for field in required_fields if not request.form.get(field)]

if missing_fields:
    return jsonify({
        'success': False,
        'message': 'Payment link creation failed',
        'error': {
            'code': 'MISSING_REQUIRED_FIELDS',
            'details': f'Missing required fields. Received: {", ".join(request.form.keys())}'
        }
    }), 400

# Sanitize and prepare data
reference = sanitize_reference(request.form['reference'])
name = request.form['name'].strip()[:100]
description = request.form['description'].strip()[:500]
currency = request.form['currency'].strip().upper()
```

### Direct HTTP API Integration

Python requests library usage for GP API communication:

```python
def create_payment_link_api(payment_link_data: dict, access_token: str):
    """
    Create a payment link via direct GP API call.

    Args:
        payment_link_data: Payment link data structure
        access_token: Access token for authentication

    Returns:
        dict: Payment link response

    Raises:
        Exception: If payment link creation fails
    """
    headers = {
        'Content-Type': 'application/json',
        'Authorization': f'Bearer {access_token}',
        'X-GP-Version': GP_API_VERSION
    }

    response = requests.post(
        f'{GP_API_BASE_URL}/links',
        json=payment_link_data,
        headers=headers
    )

    if not response.ok:
        # Try to parse error response for better error details
        try:
            error_data = response.json()
            error_msg = error_data.get('error_description') or error_data.get('message') or response.text
        except:
            error_msg = response.text

        raise Exception(f'Payment link creation failed with status {response.status_code}: {error_msg}')

    return response.json()
```

## Dependencies

### Core Python Packages

The project uses minimal, well-maintained dependencies:

```txt
# Global Payments Pay by Link - Python Dependencies
# Core web framework and HTTP client
Flask==3.0.0
requests==2.31.0

# Environment configuration
python-dotenv==1.0.0

# Production WSGI server
gunicorn==21.2.0
```

**Key Dependencies Explained**:

- **Flask 3.0.0**: Modern, lightweight web framework with excellent performance and simplicity
- **requests 2.31.0**: The most popular Python HTTP library for API communication
- **python-dotenv 1.0.0**: Environment variable loading from .env files for configuration management
- **gunicorn 21.2.0**: Production WSGI HTTP server for high-performance deployment

### Virtual Environment Setup

```bash
# Create isolated environment
python -m venv venv
source venv/bin/activate  # Linux/macOS
# venv\Scripts\activate   # Windows

# Install dependencies
pip install -r requirements.txt

# Freeze current environment (for deployment)
pip freeze > requirements.txt

# Deactivate when done
deactivate
```

## Implementation Details

### Payment Link Configuration

Payment links are created with the following default settings:

- **Type**: PAYMENT
- **Usage Mode**: SINGLE (one-time use)
- **Usage Limit**: 1
- **Allowed Payment Methods**: CARD
- **Channel**: CNP (Card Not Present)
- **Country**: GB (United Kingdom)
- **Expiration**: 10 days from creation
- **Shipping**: YES with $0 shipping amount

### Default Payment Link Data Structure

```python
# Create PayByLink data object matching other implementations structure
expiration_date = (datetime.now() + timedelta(days=10)).strftime('%Y-%m-%d %H:%M:%S')

pay_by_link_data = {
    'account_name': account_name,
    'type': 'PAYMENT',                    # PayByLinkType::PAYMENT
    'usage_mode': 'SINGLE',               # PaymentMethodUsageMode::SINGLE
    'usage_limit': 1,                     # usageLimit = 1
    'reference': reference,
    'name': name,
    'description': description,
    'shippable': 'YES',                   # isShippable = true
    'shipping_amount': 0,                 # shippingAmount = 0
    'expiration_date': expiration_date,   # +10 days
    'transactions': {
        'allowed_payment_methods': ['CARD'],  # allowedPaymentMethods = [PaymentMethodName::CARD]
        'channel': 'CNP',                     # Card Not Present
        'country': 'GB',                      # Match other implementations
        'amount': amount,                     # Amount in cents
        'currency': currency
    },
    'notifications': {
        'return_url': 'https://www.example.com/returnUrl',     # returnUrl
        'status_url': 'https://www.example.com/statusUrl',     # statusUpdateUrl
        'cancel_url': 'https://www.example.com/returnUrl'      # cancelUrl
    }
}

# Add merchant_id if available
if merchant_id:
    pay_by_link_data['merchant_id'] = merchant_id
```

### Expiration Date Handling

```python
from datetime import datetime, timedelta

# Set expiration to 10 days from now
expiration_date = (datetime.now() + timedelta(days=10)).strftime('%Y-%m-%d %H:%M:%S')
```

## Development vs Production

### Development Configuration

The Flask application defaults to development mode with debugging features:

**Development Features**:
- **Hot Reload**: Automatic server restart on code changes
- **Debug Mode**: Detailed error pages with stack traces
- **Console Logging**: Print statements for debugging API interactions
- **Built-in Server**: Flask development server for quick testing

```python
if __name__ == '__main__':
    port = int(os.getenv('PORT', 8000))
    print(f"Server running at http://localhost:{port}")
    print("Pay by Link server ready for payment link creation!")
    app.run(host='0.0.0.0', port=port, debug=True)
```

### Production Configuration

For production deployment with enhanced security and performance:

1. **Update Environment Variables**:
   ```env
   # Production API credentials
   GP_API_APP_ID=your_production_app_id
   GP_API_APP_KEY=your_production_app_key
   GP_API_ENVIRONMENT=production

   # Production server settings
   PORT=8000
   FLASK_ENV=production
   ```

2. **Update API Endpoints** (modify server.py):
   ```python
   # Change to production URLs
   GP_API_BASE_URL = 'https://apis.globalpay.com/ucp'
   ```

3. **Production WSGI Deployment**:
   ```bash
   # Basic production server
   gunicorn --bind 0.0.0.0:8000 --workers 4 server:app

   # With advanced configuration
   gunicorn --config gunicorn.conf.py server:app

   # Example gunicorn.conf.py
   bind = "0.0.0.0:8000"
   workers = 4
   worker_class = "sync"
   worker_connections = 1000
   max_requests = 1000
   max_requests_jitter = 100
   preload_app = True
   timeout = 30
   keepalive = 5
   ```

4. **Production Security Enhancements**:
   ```python
   # Disable debug mode
   app.debug = False

   # Add security headers (optional middleware)
   @app.after_request
   def add_security_headers(response):
       response.headers['X-Content-Type-Options'] = 'nosniff'
       response.headers['X-Frame-Options'] = 'DENY'
       response.headers['X-XSS-Protection'] = '1; mode=block'
       return response
   ```

### Docker Deployment

Create a Dockerfile for containerized deployment:

```dockerfile
FROM python:3.11-slim

WORKDIR /app

# Install dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application files
COPY server.py index.html ./

# Create logs directory
RUN mkdir -p logs

EXPOSE 8000

# Use gunicorn for production
CMD ["gunicorn", "--bind", "0.0.0.0:8000", "--workers", "4", "server:app"]
```

```bash
# Build and run with Docker
docker build -t python-pay-by-link .
docker run -p 8000:8000 --env-file .env python-pay-by-link
```

## Error Handling

### Python Exception Handling

The application implements comprehensive error handling with try-catch blocks and structured responses:

```python
try:
    # Generate access token
    token_response = generate_access_token()
    if not token_response.get('token'):
        return jsonify({
            'success': False,
            'message': 'Payment link creation failed',
            'error': {
                'code': 'TOKEN_GENERATION_ERROR',
                'details': 'Failed to generate access token'
            }
        }), 400

    # Create payment link via GP API
    link_response = create_payment_link_api(pay_by_link_data, access_token)

    payment_link = link_response.get('url')
    if not payment_link:
        return jsonify({
            'success': False,
            'message': 'Payment link creation failed',
            'error': {
                'code': 'INVALID_RESPONSE',
                'details': 'No payment link URL in response'
            }
        }), 400

    # Return success response
    return jsonify({
        'success': True,
        'message': f'Payment link created successfully! Link ID: {link_response.get("id")}',
        'data': {
            'paymentLink': payment_link,
            'linkId': link_response.get('id'),
            'reference': reference,
            'amount': amount,
            'currency': currency
        }
    })

except Exception as error:
    print(f'Payment link creation failed: {error}')

    # Handle different error types
    error_code = 'UNKNOWN_ERROR'
    error_details = str(error)

    if 'Failed to generate access token' in error_details or 'Token request failed' in error_details:
        error_code = 'TOKEN_GENERATION_ERROR'
    elif 'Payment link creation failed' in error_details or 'Invalid response' in error_details:
        error_code = 'API_ERROR'

    return jsonify({
        'success': False,
        'message': 'Payment link creation failed',
        'error': {
            'code': error_code,
            'details': error_details
        }
    }), 500
```

### Error Codes

- `MISSING_REQUIRED_FIELDS`: Required form parameters not provided
- `INVALID_AMOUNT`: Amount is not a positive integer
- `TOKEN_GENERATION_ERROR`: Failed to generate GP API access token
- `API_ERROR`: Error response from Global Payments API
- `INVALID_RESPONSE`: Malformed or missing response from GP API
- `UNKNOWN_ERROR`: Unexpected server-side processing errors

### HTTP Status Codes

- `200 OK`: Successful payment link creation or configuration retrieval
- `400 Bad Request`: Invalid request parameters or client-side errors
- `500 Internal Server Error`: Server-side processing errors

## Security Features

- **Input Sanitization**: All user inputs sanitized using Python regex patterns and string methods
- **Reference Sanitization**: Removes potentially harmful characters using regex: `[^\w\s\-#]`
- **Length Limits**: Enforced on all text fields (reference: 100 chars, name: 100 chars, description: 500 chars)
- **Amount Validation**: Ensures positive integer amounts with type conversion and validation
- **Environment Isolation**: Clear separation between sandbox and production endpoints
- **Token Security**: Access tokens generated fresh for each request
- **Type Hints**: Enhanced code safety with Python type annotations
- **Error Information**: Error responses don't expose sensitive internal system details
- **HTTPS Enforcement**: Production deployments should enforce HTTPS-only communication
- **Environment Variables**: Sensitive credentials stored in environment variables, not code

## Troubleshooting

### Common Issues

1. **Missing Environment Variables**
   ```
   Exception: Missing GP_API_APP_ID or GP_API_APP_KEY environment variables
   ```
   **Solution**: Ensure `.env` file exists and contains valid `GP_API_APP_ID` and `GP_API_APP_KEY`

2. **Python Version Issues**
   ```
   SyntaxError: invalid syntax (type hints require Python 3.6+)
   ```
   **Solution**: Upgrade to Python 3.8+ and verify with `python --version`

3. **Virtual Environment Issues**
   ```
   ModuleNotFoundError: No module named 'flask'
   ```
   **Solution**: Activate virtual environment and install dependencies:
   ```bash
   source venv/bin/activate
   pip install -r requirements.txt
   ```

4. **Port Already in Use**
   ```
   OSError: [Errno 48] Address already in use
   ```
   **Solution**: Change port or kill existing process:
   ```bash
   PORT=5000 python server.py
   # Or find and kill process using port 8000
   lsof -ti:8000 | xargs kill -9
   ```

5. **Requests Library Issues**
   ```
   requests.exceptions.SSLError: [SSL: CERTIFICATE_VERIFY_FAILED]
   ```
   **Solution**: Update certificates or check network configuration:
   ```bash
   pip install --upgrade certifi requests
   ```

6. **Token Generation Failures**
   ```
   Exception: Token request failed with status 401: Unauthorized
   ```
   **Solution**: Verify API credentials are correct and match environment (sandbox/production)

7. **Flask Import Errors**
   ```
   ImportError: cannot import name 'Flask' from 'flask'
   ```
   **Solution**: Reinstall Flask and verify virtual environment:
   ```bash
   pip uninstall flask
   pip install Flask==3.0.0
   ```

8. **Form Data Processing Issues**
   ```
   KeyError: 'amount'
   ```
   **Solution**: Ensure requests use `Content-Type: application/x-www-form-urlencoded` and include all required fields

### Testing the API

Test the Flask endpoints:

```bash
# Test configuration endpoint
curl http://localhost:8000/config

# Test payment link creation
curl -X POST http://localhost:8000/create-payment-link \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d 'amount=1000&currency=USD&reference=Test%20Payment&name=Test%20Product&description=Testing%20payment%20link%20creation%20with%20Python%20Flask'

# Test with Python requests
python -c "
import requests
resp = requests.get('http://localhost:8000/config')
print(resp.json())
"
```

### Development Tools

**Virtual Environment Best Practices**:
```bash
# Create project-specific environment
python -m venv venv --prompt="paybylink"

# Install development tools
pip install pytest black flake8 mypy

# Code formatting and linting
black server.py
flake8 server.py
mypy server.py

# Testing (create test files)
pytest tests/
```

**Flask Development Tools**:
```bash
# Enable debug mode
export FLASK_DEBUG=1
flask --app server run --reload

# Run with auto-reload on file changes
python server.py  # Built-in debug mode

# Profile performance
python -m cProfile server.py
```

**Environment Management**:
```bash
# Export current environment
pip freeze > requirements.txt

# Create requirements for different environments
pip freeze | grep -E "(Flask|requests|python-dotenv)" > requirements-base.txt
pip freeze > requirements-dev.txt  # Include development dependencies
```

## Support

- **Global Payments Documentation**: [Global Payments Developer Portal](https://developer.globalpay.com/)
- **API Reference**: [Global Payments API Documentation](https://developer.globalpay.com/api)
- **Flask Documentation**: [Flask Official Documentation](https://flask.palletsprojects.com/)
- **Python Requests**: [Requests Documentation](https://docs.python-requests.org/)
- **Python Virtual Environments**: [Python venv Documentation](https://docs.python.org/3/tutorial/venv.html)
- **Gunicorn Deployment**: [Gunicorn Documentation](https://gunicorn.org/)
