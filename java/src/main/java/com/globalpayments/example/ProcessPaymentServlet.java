package com.globalpayments.example;

import io.github.cdimascio.dotenv.Dotenv;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.global.api.ServicesContainer;
import com.global.api.entities.Transaction;
import com.global.api.entities.PayByLinkData;
import com.global.api.entities.enums.Channel;
import com.global.api.entities.enums.Environment;
import com.global.api.entities.enums.PayByLinkType;
import com.global.api.entities.enums.PaymentMethodUsageMode;
import com.global.api.entities.enums.PaymentMethodName;
import com.global.api.entities.enums.Target;
import com.global.api.entities.exceptions.ApiException;
import com.global.api.entities.gpApi.entities.AccessTokenInfo;
import com.global.api.serviceConfigs.GpApiConfig;
import com.global.api.services.PayByLinkService;
import com.global.api.utils.GenerationUtils;
import org.joda.time.DateTime;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * Pay by Link Creation Servlet
 *
 * This servlet demonstrates payment link creation using the Global Payments GP API.
 * It provides endpoints for payment link creation.
 *
 * Endpoints:
 * - GET /config: Returns config for client-side use
 * - POST /create-payment-link: Creates payment links for customer payments
 *
 * @author Global Payments
 * @version 1.0
 */

@WebServlet(urlPatterns = {"/create-payment-link", "/config"})
public class ProcessPaymentServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Dotenv dotenv = Dotenv.load();
    private String environment;

    /**
     * Initializes the servlet with GP API settings.
     * Configures the Global Payments SDK.
     */
    @Override
    public void init() throws ServletException {
        // Set environment
        environment = "sandbox";

        // Configure SDK
        try {
            configureSdk();
        } catch (Exception e) {
            throw new ServletException("Failed to configure Global Payments SDK", e);
        }
    }

    /**
     * Configure the Global Payments SDK
     * Sets up the SDK with necessary credentials and settings loaded from environment variables.
     */
    private void configureSdk() throws Exception {
        GpApiConfig config = new GpApiConfig();
        config.setAppId(dotenv.get("GP_API_APP_ID"));
        config.setAppKey(dotenv.get("GP_API_APP_KEY"));
        config.setEnvironment(Environment.TEST); // Use Environment.PRODUCTION for live transactions
        config.setChannel(Channel.CardNotPresent);
        config.setCountry("GB");

        // Set up access token info for Pay by Link
        AccessTokenInfo accessTokenInfo = new AccessTokenInfo();
        accessTokenInfo.setTransactionProcessingAccountName("paylink");
        config.setAccessTokenInfo(accessTokenInfo);

        ServicesContainer.configureService(config);
    }

    /**
     * Handles GET requests to /config endpoint.
     * Returns config data for the client.
     *
     * @param request The HTTP request
     * @param response The HTTP response
     * @throws ServletException If there's an error in servlet processing
     * @throws IOException If there's an I/O error
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (request.getServletPath().equals("/config")) {
            response.setContentType("application/json");
            String jsonResponse = String.format(
                "{\"success\":true,\"data\":{\"environment\":\"%s\",\"supportedCurrencies\":[\"EUR\",\"USD\",\"GBP\"],\"supportedPaymentMethods\":[\"CARD\"]}}",
                environment
            );
            response.getWriter().write(jsonResponse);
        }
    }

    /**
     * Sanitizes reference string by removing potentially harmful characters.
     * Sanitizes reference string by removing potentially harmful characters.
     *
     * @param reference The reference to sanitize, can be null
     * @return A sanitized reference containing only safe characters (alphanumeric, spaces, hyphens, hash),
     *         limited to 100 characters. Returns empty string if input is null.
     */
    private String sanitizeReference(String reference) {
        if (reference == null) {
            return "";
        }
        // Remove non-alphanumeric characters except spaces, hyphens, and hash
        // Remove non-alphanumeric characters except spaces, hyphens, and hash
        String sanitized = reference.replaceAll("[^\\w\\s\\-#]", "");
        return sanitized.length() > 100 ? sanitized.substring(0, 100) : sanitized;
    }


    /**
     * Handles POST requests to /create-payment-link endpoint.
     * Creates payment links using GP API.
     *
     * @param request The HTTP request containing payment link details
     * @param response The HTTP response
     * @throws ServletException If there's an error in servlet processing
     * @throws IOException If there's an I/O error
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");

        try {
            // Validate required fields
            String[] requiredFields = {"amount", "currency", "reference", "name", "description"};
            StringBuilder receivedFields = new StringBuilder();
            boolean hasAllFields = true;

            for (String field : requiredFields) {
                String value = request.getParameter(field);
                if (value == null || value.trim().isEmpty()) {
                    hasAllFields = false;
                }
                if (receivedFields.length() > 0) receivedFields.append(", ");
                receivedFields.append(field);
            }

            if (!hasAllFields) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                String errorResponse = String.format(
                    "{\"success\":false,\"message\":\"Payment link creation failed\",\"error\":{\"code\":\"MISSING_REQUIRED_FIELDS\",\"details\":\"Missing required fields. Received: %s\"}}",
                    receivedFields.toString()
                );
                response.getWriter().write(errorResponse);
                return;
            }

            // Parse and validate amount
            int amount;
            try {
                amount = Integer.parseInt(request.getParameter("amount"));
                if (amount <= 0) {
                    throw new NumberFormatException("Amount must be positive");
                }
            } catch (NumberFormatException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                String errorResponse = "{\"success\":false,\"message\":\"Payment link creation failed\",\"error\":{\"code\":\"INVALID_AMOUNT\",\"details\":\"Invalid amount\"}}";
                response.getWriter().write(errorResponse);
                return;
            }

            // Sanitize and prepare data
            String reference = sanitizeReference(request.getParameter("reference"));
            String name = request.getParameter("name").trim();
            if (name.length() > 100) name = name.substring(0, 100);

            String description = request.getParameter("description").trim();
            if (description.length() > 500) description = description.substring(0, 500);

            String currency = request.getParameter("currency").trim().toUpperCase();

            // Create payment link using SDK
            String paymentLinkResponse = createPaymentLinkViaSdk(amount, currency, reference, name, description);

            response.getWriter().write(paymentLinkResponse);

        } catch (ApiException e) {
            // Handle API-specific errors
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            String errorResponse = String.format(
                "{\"success\":false,\"message\":\"Payment link creation failed\",\"error\":{\"code\":\"API_ERROR\",\"details\":\"%s\"}}",
                e.getMessage().replace("\"", "\\\"")
            );
            response.getWriter().write(errorResponse);
        } catch (Exception e) {
            // Handle other errors
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            String errorResponse = String.format(
                "{\"success\":false,\"message\":\"Payment link creation failed\",\"error\":{\"code\":\"INTERNAL_ERROR\",\"details\":\"%s\"}}",
                e.getMessage().replace("\"", "\\\"")
            );
            response.getWriter().write(errorResponse);
        }
    }

    /**
     * Creates a payment link using the Global Payments SDK.
     * Matches the implementation approach using PayByLinkService.
     *
     * @param amount Amount in cents
     * @param currency Currency code
     * @param reference Payment reference
     * @param name Payment name
     * @param description Payment description
     * @return JSON response string
     * @throws ApiException If there's an error in the API call
     */
    private String createPaymentLinkViaSdk(int amount, String currency, String reference, String name, String description) throws ApiException {
        try {
            // Create PayByLinkData request object
            PayByLinkData payByLink = new PayByLinkData();
            payByLink.setType(PayByLinkType.PAYMENT);
            payByLink.setUsageMode(PaymentMethodUsageMode.SINGLE);
            payByLink.setAllowedPaymentMethods(new String[]{PaymentMethodName.Card.getValue(Target.GP_API)});
            payByLink.setUsageLimit(1);
            payByLink.setName(name);
            payByLink.isShippable(true);
            payByLink.setShippingAmount(new BigDecimal("0"));

            // Set expiration date to 10 days from now (using Joda DateTime)
            payByLink.setExpirationDate(DateTime.now().plusDays(10));

            // Set URLs for notifications
            payByLink.setReturnUrl("https://www.example.com/returnUrl");
            payByLink.setStatusUpdateUrl("https://www.example.com/statusUrl");
            payByLink.setCancelUrl("https://www.example.com/returnUrl");

            // Create the payment link using PayByLinkService (amount in dollars, not cents)
            Transaction transactionResponse = PayByLinkService
                .create(payByLink, new BigDecimal(amount).divide(new BigDecimal(100)))
                .withCurrency(currency)
                .withClientTransactionId(GenerationUtils.generateRecurringKey())
                .withDescription(description)
                .execute();

            // Extract payment link URL from response
            String paymentLink = transactionResponse.getPayByLinkResponse().getUrl();
            String linkId = transactionResponse.getPayByLinkResponse().getId();

            // Return success response
            return String.format(
                "{\"success\":true,\"message\":\"Payment link created successfully! Link ID: %s\",\"data\":{\"paymentLink\":\"%s\",\"linkId\":\"%s\",\"reference\":\"%s\",\"amount\":%d,\"currency\":\"%s\"}}",
                linkId, paymentLink, linkId, reference, amount, currency
            );

        } catch (ApiException e) {
            // Handle payment link creation errors
            return String.format(
                "{\"success\":false,\"message\":\"Payment link creation failed\",\"error\":{\"code\":\"API_ERROR\",\"details\":\"%s\"}}",
                e.getMessage().replace("\"", "\\\"")
            );
        }
    }

}
