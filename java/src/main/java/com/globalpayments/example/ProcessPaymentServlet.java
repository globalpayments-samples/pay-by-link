package com.globalpayments.example;

import io.github.cdimascio.dotenv.Dotenv;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.UUID;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;

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
     */
    @Override
    public void init() throws ServletException {
        // Set environment
        environment = "sandbox";
        // GP API Pay by Link servlet initialized
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
     * Formats a date for payment link expiration (10 days from now).
     * Formats a date for payment link expiration (10 days from now).
     *
     * @return Formatted date string for expiration
     */
    private String getExpirationDate() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, 10);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        return sdf.format(calendar.getTime());
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

            // Create payment link using direct API call (matching Node.js approach)
            String paymentLinkResponse = createPaymentLinkViaAPI(amount, currency, reference, name, description);

            response.getWriter().write(paymentLinkResponse);

        } catch (Exception e) {
            // Handle payment link creation errors
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            String errorResponse = String.format(
                "{\"success\":false,\"message\":\"Payment link creation failed\",\"error\":{\"code\":\"API_ERROR\",\"details\":\"%s\"}}",
                e.getMessage().replace("\"", "\\\"")
            );
            response.getWriter().write(errorResponse);
        }
    }

    /**
     * Creates a payment link using direct GP API HTTP calls.
     * Matches the Node.js implementation approach with access token generation.
     *
     * @param amount Amount in cents
     * @param currency Currency code
     * @param reference Payment reference
     * @param name Payment name
     * @param description Payment description
     * @return JSON response string
     * @throws IOException If there's an error in the HTTP request
     */
    private String createPaymentLinkViaAPI(int amount, String currency, String reference, String name, String description) throws IOException {
        try {
            // Generate access token first using SDK
            String[] tokenData = generateAccessToken();
            String accessToken = tokenData[0];
            String merchantId = tokenData[1];
            String accountName = tokenData[2];

            // Create PayByLink data object matching Node.js implementation
            String expirationDate = getExpirationDate();

            String payByLinkJson = String.format(
                "{" +
                "\"account_name\":\"%s\"," +
                "\"type\":\"PAYMENT\"," +
                "\"usage_mode\":\"SINGLE\"," +
                "\"usage_limit\":1," +
                "\"reference\":\"%s\"," +
                "\"name\":\"%s\"," +
                "\"description\":\"%s\"," +
                "\"shippable\":\"YES\"," +
                "\"shipping_amount\":0," +
                "\"expiration_date\":\"%s\"," +
                "\"transactions\":{" +
                    "\"allowed_payment_methods\":[\"CARD\"]," +
                    "\"channel\":\"CNP\"," +
                    "\"country\":\"GB\"," +
                    "\"amount\":%d," +
                    "\"currency\":\"%s\"" +
                "}," +
                "\"notifications\":{" +
                    "\"return_url\":\"https://www.example.com/returnUrl\"," +
                    "\"status_url\":\"https://www.example.com/statusUrl\"," +
                    "\"cancel_url\":\"https://www.example.com/returnUrl\"" +
                "}" +
                (merchantId != null ? ",\"merchant_id\":\"" + merchantId + "\"" : "") +
                "}",
                accountName, reference, name, description, expirationDate, amount, currency
            );

            // Make API call to create payment link
            URL url = new URL("https://apis.sandbox.globalpay.com/ucp/links");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setRequestProperty("X-GP-Version", "2021-03-22");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Accept-Encoding", "gzip, deflate");
            conn.setRequestProperty("User-Agent", "PayByLink-Java/1.0");
            conn.setDoOutput(true);

            // Send request
            try (OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream())) {
                writer.write(payByLinkJson);
            }

            // Read response
            int responseCode = conn.getResponseCode();
            StringBuilder responseBody = new StringBuilder();

            // Use helper method to handle compressed responses
            boolean isSuccessResponse = (responseCode >= 200 && responseCode < 300);
            try (InputStream inputStream = getResponseStream(conn, isSuccessResponse);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    responseBody.append(line);
                }
            }

            if (responseCode >= 200 && responseCode < 300) {
                // Parse successful response
                String responseStr = responseBody.toString();

                // Extract payment link URL and ID using simple string manipulation
                String paymentLink = extractJsonValue(responseStr, "url");
                String linkId = extractJsonValue(responseStr, "id");

                if (paymentLink == null || linkId == null) {
                    throw new IOException("Invalid response format: missing url or id");
                }

                // Return success response
                return String.format(
                    "{\"success\":true,\"message\":\"Payment link created successfully! Link ID: %s\",\"data\":{\"paymentLink\":\"%s\",\"linkId\":\"%s\",\"reference\":\"%s\",\"amount\":%d,\"currency\":\"%s\"}}",
                    linkId, paymentLink, linkId, reference, amount, currency
                );
            } else {
                // Handle API error
                String errorDetails = responseBody.toString();
                try {
                    // Try to extract error message from JSON
                    String errorMsg = extractJsonValue(errorDetails, "error_description");
                    if (errorMsg == null) {
                        errorMsg = extractJsonValue(errorDetails, "message");
                    }
                    if (errorMsg != null) {
                        errorDetails = errorMsg;
                    }
                } catch (Exception ignored) {
                    // Use raw error if JSON parsing fails
                }

                return String.format(
                    "{\"success\":false,\"message\":\"Payment link creation failed\",\"error\":{\"code\":\"API_ERROR\",\"details\":\"%s\",\"responseCode\":%d}}",
                    errorDetails.replace("\"", "\\\""), responseCode
                );
            }

        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Payment link creation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Helper method to get the appropriate input stream, handling compressed responses.
     *
     * @param conn HttpURLConnection to get stream from
     * @param isSuccessResponse whether this is a success response (use getInputStream) or error (use getErrorStream)
     * @return InputStream that handles decompression if needed
     * @throws IOException if stream cannot be obtained
     */
    private InputStream getResponseStream(HttpURLConnection conn, boolean isSuccessResponse) throws IOException {
        InputStream inputStream = isSuccessResponse ? conn.getInputStream() : conn.getErrorStream();

        // Check if response is gzipped
        String contentEncoding = conn.getHeaderField("Content-Encoding");
        if ("gzip".equalsIgnoreCase(contentEncoding)) {
            return new GZIPInputStream(inputStream);
        }

        return inputStream;
    }

    /**
     * Generates a secret hash using SHA512 for GP API authentication.
     * The secret is created as SHA512(NONCE + APP-KEY).
     *
     * @param nonce The nonce value
     * @param appKey The application key
     * @return SHA512 hash as lowercase hex string
     * @throws NoSuchAlgorithmException If SHA-512 algorithm is not available
     */
    private String generateSecret(String nonce, String appKey) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-512");
        md.update(nonce.getBytes(StandardCharsets.UTF_8));
        byte[] bytes = md.digest(appKey.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte aByte : bytes) {
            sb.append(Integer.toString((aByte & 0xff) + 0x100, 16).substring(1));
        }
        return sb.toString();
    }

    /**
     * Generates access token using the GP API.
     * Matches Java SDK GpApiSessionInfo.signIn approach.
     *
     * @return Array containing [accessToken, merchantId, accountName]
     * @throws Exception If token generation fails
     */
    private String[] generateAccessToken() throws Exception {
        // Create credentials for token request
        String appId = dotenv.get("GP_API_APP_ID");
        String appKey = dotenv.get("GP_API_APP_KEY");

        // Generate nonce using timestamp format (matches Java SDK)
        SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy hh:mm:ss.SSS a");
        String nonce = dateFormat.format(new Date());

        // Generate secret using SHA512 hash
        String secret = generateSecret(nonce, appKey);

        String tokenRequestJson = String.format(
            "{\"app_id\":\"%s\",\"nonce\":\"%s\",\"grant_type\":\"client_credentials\",\"secret\":\"%s\"}",
            appId, nonce, secret
        );

        // Make token request
        URL url = new URL("https://apis.sandbox.globalpay.com/ucp/accesstoken");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-GP-Api-Key", appKey);
        conn.setRequestProperty("X-GP-Version", "2021-03-22");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Accept-Encoding", "gzip, deflate");
        conn.setRequestProperty("User-Agent", "PayByLink-Java/1.0");
        conn.setDoOutput(true);

        // Send request
        try (OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream())) {
            writer.write(tokenRequestJson);
        }

        // Read response
        int responseCode = conn.getResponseCode();
        StringBuilder responseBody = new StringBuilder();

        // Use helper method to handle compressed responses
        boolean isSuccessResponse = (responseCode >= 200 && responseCode < 300);
        try (InputStream inputStream = getResponseStream(conn, isSuccessResponse);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            String line;
            while ((line = reader.readLine()) != null) {
                responseBody.append(line);
            }
        }

        if (responseCode >= 200 && responseCode < 300) {
            String responseStr = responseBody.toString();

            String accessToken = extractJsonValue(responseStr, "token");

            if (accessToken == null) {
                // Try alternative field names that might be used
                accessToken = extractJsonValue(responseStr, "access_token");
                if (accessToken == null) {
                    throw new Exception("No access token in response. Full response: " + responseStr);
                }
            }

            // Extract additional information if available
            String merchantId = extractJsonValue(responseStr, "merchant_id");
            String accountName = extractJsonValue(responseStr, "transaction_processing_account_name");
            if (accountName == null) {
                accountName = "paylink"; // Default account name
            }

            return new String[]{accessToken, merchantId, accountName};
        } else {
            String errorResponse = responseBody.toString();
            throw new Exception("Failed to generate access token (HTTP " + responseCode + "): " + errorResponse);
        }
    }

    /**
     * Simple JSON value extractor for parsing API responses.
     * Handles basic string value extraction without full JSON parsing library.
     * Now properly handles whitespace around colons and values.
     *
     * @param json JSON string
     * @param key Key to extract
     * @return Extracted value or null if not found
     */
    private String extractJsonValue(String json, String key) {
        if (json == null || key == null) {
            return null;
        }

        // Look for the key pattern with flexible whitespace: "key" : "value"
        String keyPattern = "\"" + key + "\"";
        int keyIndex = json.indexOf(keyPattern);
        if (keyIndex == -1) {
            return null;
        }

        // Move past the key
        int colonIndex = keyIndex + keyPattern.length();

        // Skip whitespace and find the colon
        while (colonIndex < json.length() && Character.isWhitespace(json.charAt(colonIndex))) {
            colonIndex++;
        }

        if (colonIndex >= json.length() || json.charAt(colonIndex) != ':') {
            return null;
        }

        // Move past the colon
        colonIndex++;

        // Skip whitespace after colon
        while (colonIndex < json.length() && Character.isWhitespace(json.charAt(colonIndex))) {
            colonIndex++;
        }

        if (colonIndex >= json.length()) {
            return null;
        }

        // Check if the value is a quoted string
        if (json.charAt(colonIndex) == '"') {
            // It's a quoted string - find the closing quote
            int valueStart = colonIndex + 1;
            int valueEnd = json.indexOf('"', valueStart);
            if (valueEnd != -1) {
                return json.substring(valueStart, valueEnd);
            }
        } else {
            // It's an unquoted value - find the end
            int valueStart = colonIndex;
            int valueEnd = valueStart;

            while (valueEnd < json.length()) {
                char ch = json.charAt(valueEnd);
                if (ch == ',' || ch == '}' || ch == ']' || ch == '\n' || ch == '\r') {
                    break;
                }
                valueEnd++;
            }

            if (valueEnd > valueStart) {
                return json.substring(valueStart, valueEnd).trim();
            }
        }

        return null;
    }
}
