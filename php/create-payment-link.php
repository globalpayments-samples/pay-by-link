<?php

declare(strict_types=1);

/**
 * Create Payment Link Script
 *
 * This script demonstrates payment link creation using the Global Payments SDK.
 * It handles form data and creates secure payment links that can be shared
 * with customers via email, SMS, or other channels.
 *
 * PHP version 7.4 or higher
 *
 * @category  Payment_Processing
 * @package   GlobalPayments_Sample
 * @author    Global Payments
 * @license   MIT License
 * @link      https://github.com/globalpayments
 */

require_once 'vendor/autoload.php';

use Dotenv\Dotenv;
use GlobalPayments\Api\ServiceConfigs\Gateways\GpApiConfig;
use GlobalPayments\Api\ServicesContainer;
use GlobalPayments\Api\Entities\Enums\Environment;
use GlobalPayments\Api\Entities\Enums\Channel;
use GlobalPayments\Api\Entities\Exceptions\ApiException;
use GlobalPayments\Api\Entities\PayByLinkData;
use GlobalPayments\Api\Entities\Enums\PayByLinkType;
use GlobalPayments\Api\Entities\Enums\PaymentMethodUsageMode;
use GlobalPayments\Api\Entities\Enums\PaymentMethodName;
use GlobalPayments\Api\Services\PayByLinkService;
use GlobalPayments\Api\Utils\GenerationUtils;
use GlobalPayments\Api\Entities\GpApi\AccessTokenInfo;
use GlobalPayments\Api\Utils\Logging\Logger;
use GlobalPayments\Api\Utils\Logging\SampleRequestLogger;

ini_set('display_errors', '0');

/**
 * Configure the SDK
 *
 * Sets up the Global Payments SDK with necessary credentials and settings
 * loaded from environment variables.
 *
 * @return void
 */
function configureSdk(): void
{
    $dotenv = Dotenv::createImmutable(__DIR__);
    $dotenv->load();

    $config = new GpApiConfig();
    $config->appId = $_ENV['GP_API_APP_ID'] ?? '';
    $config->appKey = $_ENV['GP_API_APP_KEY'] ?? '';
    $config->environment = Environment::TEST; // Use Environment::PRODUCTION for live transactions
    $config->channel = Channel::CardNotPresent;
    $config->country = 'GB';

    // Set up access token info for Pay by Link
    $accessTokenInfo = new AccessTokenInfo();
    $accessTokenInfo->transactionProcessingAccountName = 'paylink';
    $config->accessTokenInfo = $accessTokenInfo;

    $config->requestLogger = new SampleRequestLogger(new Logger("logs"));

    ServicesContainer::configureService($config);
}

/**
 * Sanitize reference string by removing potentially harmful characters
 *
 * @param string|null $reference The reference to sanitize
 *
 * @return string Sanitized reference containing only safe characters,
 *                limited to 100 characters
 */
function sanitizeReference(?string $reference): string
{
    if ($reference === null) {
        return '';
    }

    $sanitized = preg_replace('/[^\w\s\-#]/', '', $reference);
    return substr($sanitized, 0, 100);
}

// Initialize SDK configuration
configureSdk();

try {
    // Debug: Log received POST data
    error_log('Received POST data: ' . print_r($_POST, true));

    // Validate required fields
    if (!isset($_POST['amount'], $_POST['currency'], $_POST['reference'], $_POST['name'], $_POST['description'])) {
        throw new ApiException('Missing required fields. Received: ' . implode(', ', array_keys($_POST)));
    }
    

    // Parse and validate amount
    $amount = intval($_POST['amount']);
    if ($amount <= 0) {
        throw new ApiException('Invalid amount');
    }

    // Sanitize and prepare data
    $reference = sanitizeReference($_POST['reference']);
    $name = substr(trim($_POST['name']), 0, 100);
    $description = substr(trim($_POST['description']), 0, 500);
    $currency = strtoupper(trim($_POST['currency']));

    // Create PayByLinkData object following the test pattern
    $payByLink = new PayByLinkData();
    $payByLink->type = PayByLinkType::PAYMENT;
    $payByLink->usageMode = PaymentMethodUsageMode::SINGLE;
    $payByLink->allowedPaymentMethods = [PaymentMethodName::CARD];
    $payByLink->usageLimit = 1;
    $payByLink->name = $name;
    $payByLink->isShippable = true;
    $payByLink->shippingAmount = 0;
    $payByLink->expirationDate = date('Y-m-d H:i:s', strtotime('+10 days'));
    $payByLink->images = [];
    $payByLink->returnUrl = 'https://www.example.com/returnUrl';
    $payByLink->statusUpdateUrl = 'https://www.example.com/statusUrl';
    $payByLink->cancelUrl = 'https://www.example.com/returnUrl';

    error_log('Creating payment link with amount: ' . ($amount / 100) . ', currency: ' . $currency . ', name: ' . $name);

    // Create the payment link using PayByLinkService (amount in dollars)
    $response = PayByLinkService::create($payByLink, $amount / 100)
        ->withCurrency($currency)
        ->withClientTransactionId(GenerationUtils::getGuid())
        ->withDescription($description)
        ->execute();

    // Extract payment link URL from response
    $paymentLink = $response->payByLinkResponse->url;

    // Return success response with payment link details
    echo json_encode([
        'success' => true,
        'message' => 'Payment link created successfully! Link ID: ' . $response->payByLinkResponse->id,
        'data' => [
            'paymentLink' => $paymentLink,
            'linkId' => $response->payByLinkResponse->id,
            'reference' => $reference,
            'amount' => $amount,
            'currency' => $currency
        ]
    ]);

} catch (ApiException $e) {
    // Handle payment link creation errors
    http_response_code(400);
    echo json_encode([
        'success' => false,
        'message' => 'Payment link creation failed',
        'error' => [
            'code' => 'API_ERROR',
            'details' => $e->getMessage(),
            'responseCode' => $e
        ]
    ]);
}