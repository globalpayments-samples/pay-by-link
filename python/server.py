"""
Global Payments Pay by Link - Python Flask

This Flask application provides Pay by Link functionality using the Global Payments API.
Creates secure payment links that can be shared with customers via email, SMS, or other channels.
"""

import os
import re
import json
import hashlib
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

def generate_secret(nonce: str, app_key: str) -> str:
    """
    Generate a secret hash using SHA512 for GP API authentication.
    The secret is created as SHA512(NONCE + APP-KEY).

    Args:
        nonce: The nonce value
        app_key: The application key

    Returns:
        SHA512 hash as lowercase hex string
    """
    combined = nonce + app_key
    return hashlib.sha512(combined.encode()).hexdigest().lower()

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

    # Generate nonce using the same format as other implementations
    nonce = datetime.now().strftime('%m/%d/%Y %I:%M:%S.%f %p')[:-3] + ' ' + datetime.now().strftime('%p')

    token_request = {
        'app_id': GP_API_APP_ID,
        'nonce': nonce,
        'grant_type': 'client_credentials',
        'secret': generate_secret(nonce, GP_API_APP_KEY)
    }

    headers = {
        'Content-Type': 'application/json',
        'X-GP-Api-Key': GP_API_APP_KEY,
        'X-GP-Version': GP_API_VERSION,
        'Accept': 'application/json',
        'User-Agent': 'PayByLink-Python/1.0'
    }

    response = requests.post(
        f'{GP_API_BASE_URL}/accesstoken',
        json=token_request,
        headers=headers
    )

    if not response.ok:
        raise Exception(f'Token request failed with status {response.status_code}: {response.text}')

    return response.json()

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
        'X-GP-Version': GP_API_VERSION,
        'Accept': 'application/json',
        'User-Agent': 'PayByLink-Python/1.0'
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

@app.route('/')
def index():
    """Serve the main HTML page."""
    return app.send_static_file('index.html')

@app.route('/config')
def get_config():
    """
    Config endpoint - provides config for client-side use.
    Returns standardized response.
    """
    return jsonify({
        'success': True,
        'data': {
            'environment': 'sandbox',
            'supportedCurrencies': ['EUR', 'USD', 'GBP'],
            'supportedPaymentMethods': ['CARD']
        }
    })

@app.route('/create-payment-link', methods=['POST'])
def create_payment_link():
    """
    Create payment link endpoint.
    Creates a new payment link using Global Payments API with direct HTTP calls.
    Replicates functionality from other language implementations exactly.
    """
    try:
        # Log request for debugging (remove in production)
        if os.getenv('FLASK_ENV') == 'development':
            print(f'Received payment link request for {request.form.get("currency", "unknown")} {request.form.get("amount", "0")} cents')

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

        # Sanitize and prepare data matching other implementations
        reference = sanitize_reference(request.form['reference'])
        name = request.form['name'].strip()[:100]
        description = request.form['description'].strip()[:500]
        currency = request.form['currency'].strip().upper()

        # Log payment link creation
        if os.getenv('FLASK_ENV') == 'development':
            print(f'Creating payment link: {amount} cents {currency}')

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

        access_token = token_response['token']
        merchant_id = token_response.get('merchant_id')
        account_name = token_response.get('transaction_processing_account_name', 'paylink')

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

        # Log API call
        if os.getenv('FLASK_ENV') == 'development':
            print('Calling GP API to create payment link')

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

        # Return success response matching other implementations format exactly
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
        # Log error for debugging
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

# Add your custom endpoints here
# Examples:
# @app.route('/get-link-status/<link_id>', methods=['GET'])
# def get_link_status(link_id):
#     # Get payment link status logic
#     pass
#
# @app.route('/cancel-link/<link_id>', methods=['POST'])
# def cancel_link(link_id):
#     # Cancel payment link logic
#     pass

# Start the server if this file is run directly
if __name__ == '__main__':
    port = int(os.getenv('PORT', 8000))
    print(f"Server running at http://localhost:{port}")
    print("Pay by Link server ready for payment link creation!")
    app.run(host='0.0.0.0', port=port, debug=True)