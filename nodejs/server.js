/**
 * Global Payments Pay by Link - Node.js
 *
 * This Express application provides Pay by Link functionality using the Global Payments API.
 * Creates secure payment links that can be shared with customers via email, SMS, or other channels.
 */

import express from 'express';
import * as dotenv from 'dotenv';
import crypto from 'crypto';

// Load environment variables from .env file
dotenv.config();

/**
 * Initialize Express application with necessary middleware
 */
const app = express();
const port = process.env.PORT || 8000;

app.use(express.static('.')); // Serve static files
app.use(express.urlencoded({ extended: true })); // Parse form data
app.use(express.json()); // Parse JSON requests

// GP API Configuration
const GP_API_APP_ID = process.env.GP_API_APP_ID || "4gPqnGBkppGYvoE5UX9EWQlotTxGUDbs";
const GP_API_APP_KEY = process.env.GP_API_APP_KEY || "FQyJA5VuEQfcji2M";
const GP_API_BASE_URL = 'https://apis.sandbox.globalpay.com/ucp';
const GP_API_VERSION = '2021-03-22';

/**
 * Generate a secret hash using SHA512 for GP API authentication.
 * The secret is created as SHA512(NONCE + APP-KEY).
 *
 * @param {string} nonce - The nonce value
 * @param {string} appKey - The application key
 * @returns {string} SHA512 hash as lowercase hex string
 */
const generateSecret = (nonce, appKey) => {
    return crypto.createHash('sha512').update(nonce + appKey).digest('hex').toLowerCase();
};

/**
 * Generate an access token for GP API using app credentials.
 *
 * @returns {Promise<Object>} Token response containing access token and account info
 * @throws {Error} If token generation fails
 */
const generateAccessToken = async () => {
    if (!GP_API_APP_ID || !GP_API_APP_KEY) {
        throw new Error('Missing GP_API_APP_ID or GP_API_APP_KEY environment variables');
    }

    // Generate nonce using the same format as other implementations
    const now = new Date();
    const nonce = now.toLocaleString('en-US', {
        timeZone: 'UTC',
        month: '2-digit',
        day: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        fractionalSecondDigits: 3,
        hour12: true
    });

    const tokenRequest = {
        app_id: GP_API_APP_ID,
        nonce: nonce,
        grant_type: 'client_credentials',
        secret: generateSecret(nonce, GP_API_APP_KEY)
    };

    const response = await fetch(`${GP_API_BASE_URL}/accesstoken`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'X-GP-Api-Key': GP_API_APP_KEY,
            'X-GP-Version': GP_API_VERSION,
            'Accept': 'application/json',
            'User-Agent': 'PayByLink-NodeJS/1.0'
        },
        body: JSON.stringify(tokenRequest)
    });

    if (!response.ok) {
        const errorText = await response.text();
        throw new Error(`Token request failed with status ${response.status}: ${errorText}`);
    }

    return await response.json();
};

/**
 * Config endpoint - provides config for client-side use
 */
app.get('/config', (req, res) => {
    res.json({
        success: true,
        data: {
            environment: 'sandbox',
            supportedCurrencies: ['EUR', 'USD', 'GBP'],
            supportedPaymentMethods: ['CARD']
        }
    });
});

/**
 * Sanitize reference string by removing potentially harmful characters
 */
const sanitizeReference = (reference) => {
    if (!reference) {
        return '';
    }
    // Remove non-alphanumeric characters except spaces, hyphens, and hash
    const sanitized = reference.replace(/[^\w\s\-#]/g, '');
    return sanitized.substring(0, 100);
};

/**
 * Create payment link endpoint
 * Creates a new payment link using Global Payments API
 */
app.post('/create-payment-link', async (req, res) => {
    try {
        // Log request for debugging (remove in production)
        if (process.env.NODE_ENV === 'development') {
            console.log('Received payment link request');
        }

        // Validate required fields
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

        // Parse and validate amount
        const amount = parseInt(req.body.amount);
        if (amount <= 0 || isNaN(amount)) {
            return res.status(400).json({
                success: false,
                message: 'Payment link creation failed',
                error: {
                    code: 'INVALID_AMOUNT',
                    details: 'Invalid amount'
                }
            });
        }

        // Sanitize and prepare data
        const reference = sanitizeReference(req.body.reference);
        const name = req.body.name.trim().substring(0, 100);
        const description = req.body.description.trim().substring(0, 500);
        const currency = req.body.currency.trim().toUpperCase();

        // Generate access token
        const tokenResponse = await generateAccessToken();

        if (!tokenResponse.token) {
            throw new Error('Failed to generate access token');
        }

        const accessToken = tokenResponse.token;
        const merchantId = tokenResponse.merchant_id;
        const accountName = tokenResponse.transaction_processing_account_name || 'paylink';

        // Create PayByLink data object
        const payByLinkData = {
            account_name: accountName,
            type: "PAYMENT",                    // PayByLinkType::PAYMENT
            usage_mode: "SINGLE",               // PaymentMethodUsageMode::SINGLE
            usage_limit: 1,                     // usageLimit = 1
            reference: reference,
            name: name,
            description: description,
            shippable: "YES",
            shipping_amount: 0,                 // shippingAmount = 0
            expiration_date: new Date(Date.now() + (10 * 24 * 60 * 60 * 1000)).toISOString(), // +10 days
            transactions: {
                allowed_payment_methods: ["CARD"], // allowedPaymentMethods = [PaymentMethodName::CARD]
                channel: "CNP",                   // Card Not Present
                country: "GB",
                amount: amount,                   // Amount in cents
                currency: currency,
            },
            notifications: {
                return_url: "https://www.example.com/returnUrl",     // returnUrl
                status_url: "https://www.example.com/statusUrl",     // statusUpdateUrl
                cancel_url: "https://www.example.com/returnUrl",     // cancelUrl
            }
        };

        // Add merchant_id if available
        if (merchantId) {
            payByLinkData.merchant_id = merchantId;
        }

        // Make API call to create payment link
        const response = await fetch(`${GP_API_BASE_URL}/links`, {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "Authorization": `Bearer ${accessToken}`,
                "X-GP-Version": GP_API_VERSION,
                "Accept": "application/json",
                "User-Agent": "PayByLink-NodeJS/1.0"
            },
            body: JSON.stringify(payByLinkData),
        });

        if (!response.ok) {
            const errorText = await response.text();
            let errorDetails = errorText;
            try {
                const errorJson = JSON.parse(errorText);
                errorDetails = errorJson.error_description || errorJson.message || errorText;
            } catch (e) {
                // Use raw error text if JSON parsing fails
            }

            return res.status(400).json({
                success: false,
                message: 'Payment link creation failed',
                error: {
                    code: 'API_ERROR',
                    details: errorDetails,
                    responseCode: response.status
                }
            });
        }

        // Parse successful response
        const responseData = await response.json();

        // Extract payment link URL from response
        const paymentLink = responseData.url;

        if (!paymentLink) {
            throw new Error('No payment link URL in response');
        }

        // Return success response
        res.json({
            success: true,
            message: `Payment link created successfully! Link ID: ${responseData.id}`,
            data: {
                paymentLink: paymentLink,
                linkId: responseData.id,
                reference: reference,
                amount: amount,
                currency: currency
            }
        });

    } catch (error) {
        console.error('Payment link creation failed:', error);

        // Handle different error types
        let errorCode = 'UNKNOWN_ERROR';
        let errorDetails = error.message;

        if (error.message.includes('Failed to generate access token') || error.message.includes('Token request failed')) {
            errorCode = 'TOKEN_GENERATION_ERROR';
        } else if (error.message.includes('Payment link creation failed') || error.message.includes('No payment link URL')) {
            errorCode = 'API_ERROR';
        }

        res.status(500).json({
            success: false,
            message: 'Payment link creation failed',
            error: {
                code: errorCode,
                details: errorDetails
            }
        });
    }
});

/**
 * Add your custom endpoints here
 * Examples:
 * - app.post('/authorize', ...) // Authorization only
 * - app.post('/capture', ...)   // Capture authorized payment
 * - app.post('/refund', ...)    // Process refund
 * - app.get('/transaction/:id', ...) // Get transaction details
 */

// Start the server
app.listen(port, '0.0.0.0', () => {
    console.log(`Server running at http://localhost:${port}`);
    console.log(`Pay by Link server ready for payment link creation!`);
});