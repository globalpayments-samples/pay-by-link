<?php

declare(strict_types=1);

/**
 * Configuration Endpoint for Pay by Link
 *
 * This script provides configuration information for the Pay by Link interface,
 * including environment settings and supported options.
 *
 * PHP version 8.0 or higher
 *
 * @category  Configuration
 * @package   GlobalPayments_PayByLink
 * @author    Global Payments
 * @license   MIT License
 * @link      https://github.com/globalpayments
 */

require_once 'vendor/autoload.php';

use Dotenv\Dotenv;
use GlobalPayments\Api\ServiceConfigs\Gateways\GpApiConfig;
use GlobalPayments\Api\Entities\Enums\Environment;

try {
    // Load environment variables from .env file
    $dotenv = Dotenv::createImmutable(__DIR__);
    $dotenv->load();

    // Set response content type to JSON
    header('Content-Type: application/json');
    header('Access-Control-Allow-Origin: *');
    header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
    header('Access-Control-Allow-Headers: Content-Type, Authorization');

    // Handle preflight requests
    if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
        http_response_code(200);
        exit;
    }

    // Determine environment from configuration
    $environment = $_ENV['GP_API_ENVIRONMENT'] ?? 'sandbox';
    $isProduction = strtolower($environment) === 'production';

    // Return configuration for Pay by Link
    echo json_encode([
        'success' => true,
        'data' => [
            'environment' => $environment,
            'supportedCurrencies' => ['EUR', 'USD', 'GBP', 'CAD'],
            'supportedPaymentMethods' => ['CARD'],
            'defaultCurrency' => 'EUR',
            'maxAmount' => 999999, // Maximum amount in cents
            'minAmount' => 1, // Minimum amount in cents
            'usageModes' => [
                'SINGLE' => 'Single Use',
                'MULTIPLE' => 'Multiple Use'
            ],
            'api' => [
                'version' => '2021-03-22',
                'baseUrl' => $isProduction
                    ? 'https://apis.globalpay.com'
                    : 'https://apis.sandbox.globalpay.com'
            ]
        ],
    ]);
} catch (Exception $e) {
    // Handle configuration errors
    http_response_code(500);
    echo json_encode([
        'success' => false,
        'message' => 'Error loading configuration: ' . $e->getMessage()
    ]);
}
