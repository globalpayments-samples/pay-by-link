/**
 * Global Payments SDK Template - Node.js
 * 
 * This Express application provides a starting template for Global Payments SDK integration.
 * Customize the endpoints and logic below for your specific use case.
 */

import express from 'express';
import * as dotenv from 'dotenv';
import {
    ServicesContainer,
    GpApiConfig,
    Environment,
    GpApiService,
    Address,
    CreditCardData,
    ApiError
} from 'globalpayments-api';

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

// Configure Global Payments SDK with GP API credentials and settings
const config = new GpApiConfig();
config.appId = process.env.GP_API_APP_ID || "QgKeFv7BuZlDcZvUeaxdA2jRsFukThCD";
config.appKey = process.env.GP_API_APP_KEY || "eS5f8cCbuK8c6d5T";
config.environment = Environment.Test; // Use Environment.Production for live transactions
ServicesContainer.configureService(config);

/**
 * Utility function to sanitize postal code
 * Customize validation logic as needed for your use case
 */
const sanitizePostalCode = (postalCode) => {
    return postalCode.replace(/[^a-zA-Z0-9-]/g, '').slice(0, 10);
};

/**
 * Config endpoint - provides configuration for client-side use
 * Customize response data as needed
 */
app.get('/config', (req, res) => {
    res.json({
        success: true,
        data: {
            environment: config.environment === Environment.Test ? 'sandbox' : 'production',
            // Note: Never expose sensitive keys like appKey in client responses
            supportedCurrencies: ['EUR', 'USD', 'GBP'],
            supportedPaymentMethods: ['CARD']
        }
    });
});

/**
 * Create payment link endpoint
 * Creates a new payment link using the Global Payments API
 */
app.post('/create-payment-link', async (req, res) => {
    try {
        // Extract parameters from request body with defaults
        const {
            amount = 1000, // Default to 10.00 in cents
            currency = 'EUR',
            reference = 'Invoice #1234567',
            name = 'Invoice #1234567',
            description = 'Your order description'
        } = req.body;

        // Generate an access token using the Global Payments API service
        console.log('Attempting to generate access token with config:', {
            appId: config.appId,
            environment: config.environment
        });

        const tokenResponse = await GpApiService.generateTransactionKey(config);
        console.log('Token response received:', {
            hasAccessToken: !!tokenResponse.accessToken,
            hasMerchantId: !!tokenResponse.merchantId,
            hasAccountName: !!tokenResponse.transactionProcessingAccountName
        });

        // Extract access token, merchant ID, and account name from the token response
        const accessToken = tokenResponse.accessToken;
        const merchantId = tokenResponse.merchantId;
        const accountName = tokenResponse.transactionProcessingAccountName;

        // Construct the request body for creating the payment link
        const requestBody = {
            account_name: accountName,
            type: "PAYMENT",
            usage_mode: "SINGLE", // Link can be used only once
            reference: reference,
            name: name,
            description: description,
            shippable: "NO", // Indicates if the item is shippable
            transactions: {
                allowed_payment_methods: ["CARD"],
                channel: "CNP", // Card Not Present transaction
                country: "IE", // Country code for Ireland
                amount: amount,
                currency: currency,
            },
            notifications: {
                return_url: "https://www.example.com/return",
                status_url: "https://www.example.com/status",
                cancel_url: "https://www.example.com/cancel",
            },
        };

        // Only add merchant_id if it exists
        if (merchantId) {
            requestBody.merchant_id = merchantId;
        }

        // Make API call to create payment link
        console.log('Making API request to create payment link:', {
            url: "https://apis.sandbox.globalpay.com/ucp/links",
            requestBody: requestBody
        });

        const response = await fetch("https://apis.sandbox.globalpay.com/ucp/links", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "Authorization": `Bearer ${accessToken}`,
                "X-GP-Version": "2021-03-22", // API version header
            },
            body: JSON.stringify(requestBody),
        });

        console.log('API response status:', response.status);

        // Handle API response
        if (!response.ok) {
            const errorText = await response.text();
            throw new Error(`API Error: ${response.status} ${errorText}`);
        }

        // Parse the successful response and extract the payment link URL
        const responseData = await response.json();
        const paymentLink = responseData.url;

        res.json({
            success: true,
            message: 'Payment link created successfully',
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
        res.status(500).json({
            success: false,
            message: 'Payment link creation failed',
            error: error.message
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
    console.log(`Customize this template for your use case!`);
});