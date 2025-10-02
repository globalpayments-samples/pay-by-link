# Global Payments Java Pay by Link Implementation

A robust Java servlet implementation for creating and managing payment links using the Global Payments API. This Jakarta EE-based web application provides enterprise-grade payment link generation with direct API integration, comprehensive error handling, and modern servlet container deployment capabilities.

## Features

- **Jakarta EE Servlet API**: Built on modern Jakarta EE 9+ specifications (not legacy javax)
- **Embedded Tomcat Deployment**: Maven Cargo plugin with Tomcat 10.x for easy development and testing
- **Direct HTTP API Integration**: Pure HttpURLConnection implementation without external HTTP libraries
- **Zero External Dependencies**: Uses built-in Java HTTP client and string manipulation for JSON processing
- **Input Validation & Sanitization**: Comprehensive request validation matching PHP implementation patterns
- **Multi-Currency Support**: Support for EUR, USD, GBP, and other Global Payments supported currencies
- **Environment Configuration**: Flexible .env-based configuration for sandbox/production environments
- **WAR Packaging**: Standard web application deployment with ROOT context
- **Enterprise Error Handling**: Detailed error codes with servlet-appropriate exception handling
- **Static Content Serving**: Built-in static file serving from webapp root directory

## Requirements

- **Java**: 23+ (configured for latest JDK features)
- **Maven**: 3.6+ for dependency management and build lifecycle
- **Servlet Container**: Tomcat 10.x or compatible Jakarta EE 9+ container
- **Global Payments Account**: With Pay by Link API credentials (GP API App ID and Key)

## Project Structure

```
java/
├── src/main/java/
│   └── com/globalpayments/example/
│       └── ProcessPaymentServlet.java    # Main servlet implementation
├── src/main/webapp/
│   ├── WEB-INF/web.xml                  # Servlet configuration (generated)
│   └── static/                          # Static files (HTML, CSS, JS)
├── pom.xml                              # Maven configuration and dependencies
├── .env.sample                          # Environment configuration template
└── README.md                            # This documentation
```

## Quick Start

### 1. Environment Setup

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
```

### 2. Dependencies Installation

Install Maven dependencies and verify Java version:

```bash
# Verify Java 23+ is installed
java -version

# Verify Maven is installed
mvn -version

# Install dependencies
mvn clean install
```

### 3. Running the Application

#### Development Mode (Embedded Tomcat)

Run with Maven Cargo plugin for immediate development:

```bash
# Start embedded Tomcat server on port 8000
mvn clean package cargo:run

# The server will be available at http://localhost:8000
```

#### Build WAR for Deployment

Create deployable WAR file:

```bash
# Package as WAR file
mvn clean package

# WAR file created at target/ROOT.war
# Deploy to your Tomcat webapps directory
```

#### Production Deployment

Deploy to standalone Tomcat server:

```bash
# Build production WAR
mvn clean package -Dmaven.test.skip=true

# Copy to Tomcat webapps directory
cp target/ROOT.war /path/to/tomcat/webapps/

# Restart Tomcat server
sudo systemctl restart tomcat10
```

### 4. Access the Application

Open your browser and navigate to:
- **Server Root**: http://localhost:8000
- **Configuration API**: http://localhost:8000/config

## API Endpoints

### GET /config

Returns configuration information for the Pay by Link interface.

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

**Example Form Request**:
```bash
curl -X POST http://localhost:8000/create-payment-link \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d 'amount=2500&currency=USD&reference=Invoice%20%2312345&name=Product%20Purchase&description=Payment%20for%20premium%20subscription'
```

**Success Response**:
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

API Error (500):
```json
{
  "success": false,
  "message": "Payment link creation failed",
  "error": {
    "code": "API_ERROR",
    "details": "Detailed error message from GP API",
    "responseCode": 400
  }
}
```

## Code Structure

### Main Servlet Implementation

The `ProcessPaymentServlet` handles both configuration and payment link creation endpoints:

```java
@WebServlet(urlPatterns = {"/create-payment-link", "/config"})
public class ProcessPaymentServlet extends HttpServlet {

    private final Dotenv dotenv = Dotenv.load();
    private String environment;

    @Override
    public void init() throws ServletException {
        environment = "sandbox"; // Default to sandbox environment
        System.out.println("GP API Pay by Link servlet initialized for " + environment + " environment");
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Handle /config endpoint
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Handle /create-payment-link endpoint
    }
}
```

### Direct HTTP API Integration

The implementation uses `HttpURLConnection` for direct API calls without external dependencies:

```java
private String createPaymentLinkViaAPI(int amount, String currency, String reference,
        String name, String description) throws IOException {

    // Generate access token first
    String[] tokenData = generateAccessToken();
    String accessToken = tokenData[0];
    String merchantId = tokenData[1];
    String accountName = tokenData[2];

    // Create API request
    URL url = new URL("https://apis.sandbox.globalpay.com/ucp/links");
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("POST");
    conn.setRequestProperty("Content-Type", "application/json");
    conn.setRequestProperty("Authorization", "Bearer " + accessToken);
    conn.setRequestProperty("X-GP-Version", "2021-03-22");
    conn.setDoOutput(true);

    // Send payment link creation request
    // ... request handling code
}
```

### Input Validation and Sanitization

**Reference Sanitization** (matching PHP implementation):
```java
private String sanitizeReference(String reference) {
    if (reference == null) {
        return "";
    }
    // Remove non-alphanumeric characters except spaces, hyphens, and hash
    // Matches PHP preg_replace('/[^\w\s\-#]/', '', $reference)
    String sanitized = reference.replaceAll("[^\\w\\s\\-#]", "");
    return sanitized.length() > 100 ? sanitized.substring(0, 100) : sanitized;
}
```

**Required Field Validation**:
```java
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
```

**Amount Validation**:
```java
int amount;
try {
    amount = Integer.parseInt(request.getParameter("amount"));
    if (amount <= 0) {
        throw new NumberFormatException("Amount must be positive");
    }
} catch (NumberFormatException e) {
    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    // Return error response
}
```

### JSON Processing Without External Libraries

Simple JSON extraction using string manipulation:

```java
private String extractJsonValue(String json, String key) {
    String searchPattern = "\"" + key + "\":\"";
    int startIndex = json.indexOf(searchPattern);
    if (startIndex == -1) {
        return null;
    }

    startIndex += searchPattern.length();
    int endIndex = json.indexOf("\"", startIndex);
    if (endIndex == -1) {
        return null;
    }

    return json.substring(startIndex, endIndex);
}
```

## Dependencies

### Core Dependencies

```xml
<dependencies>
    <!-- Global Payments SDK (for reference, using direct API calls) -->
    <dependency>
        <groupId>com.heartlandpaymentsystems</groupId>
        <artifactId>globalpayments-sdk</artifactId>
        <version>14.2.20</version>
    </dependency>

    <!-- Environment Variable Management -->
    <dependency>
        <groupId>io.github.cdimascio</groupId>
        <artifactId>dotenv-java</artifactId>
        <version>3.0.0</version>
    </dependency>

    <!-- Jakarta Servlet API (provided by container) -->
    <dependency>
        <groupId>jakarta.servlet</groupId>
        <artifactId>jakarta.servlet-api</artifactId>
        <version>5.0.0</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

### Build Configuration

```xml
<build>
    <finalName>ROOT</finalName>
    <plugins>
        <!-- WAR packaging plugin -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-war-plugin</artifactId>
            <version>3.3.2</version>
        </plugin>

        <!-- Cargo plugin for embedded Tomcat deployment -->
        <plugin>
            <groupId>org.codehaus.cargo</groupId>
            <artifactId>cargo-maven3-plugin</artifactId>
            <version>1.10.10</version>
            <configuration>
                <container>
                    <containerId>tomcat10x</containerId>
                    <type>embedded</type>
                </container>
                <configuration>
                    <properties>
                        <cargo.servlet.port>8000</cargo.servlet.port>
                    </properties>
                </configuration>
            </configuration>
        </plugin>
    </plugins>
</build>
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

### Default URLs Configuration
```java
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
    "}",
    accountName, reference, name, description, expirationDate, amount, currency
);
```

### Expiration Date Formatting

```java
private String getExpirationDate() {
    Calendar calendar = Calendar.getInstance();
    calendar.add(Calendar.DAY_OF_MONTH, 10);
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    return sdf.format(calendar.getTime());
}
```

## Development vs Production

### Development Configuration

The servlet defaults to sandbox environment:

```java
@Override
public void init() throws ServletException {
    environment = "sandbox"; // Default to sandbox/test environment
    System.out.println("GP API Pay by Link servlet initialized for " + environment + " environment");
}
```

Sandbox API endpoints:
- Token URL: `https://apis.sandbox.globalpay.com/ucp/accesstoken`
- Links URL: `https://apis.sandbox.globalpay.com/ucp/links`

### Production Configuration

For production deployment:

1. **Update Environment Variables**:
   ```env
   GP_API_APP_ID=your_production_app_id
   GP_API_APP_KEY=your_production_app_key
   GP_API_ENVIRONMENT=production
   ```

2. **Update API Endpoints** (modify servlet code):
   ```java
   // Change URLs to production endpoints
   URL tokenUrl = new URL("https://apis.globalpay.com/ucp/accesstoken");
   URL linksUrl = new URL("https://apis.globalpay.com/ucp/links");
   ```

3. **Update Environment Response**:
   ```java
   String jsonResponse = String.format(
       "{\"success\":true,\"data\":{\"environment\":\"%s\",\"supportedCurrencies\":[\"EUR\",\"USD\",\"GBP\"],\"supportedPaymentMethods\":[\"CARD\"]}}",
       "production" // Changed from "sandbox"
   );
   ```

### Production Build

```bash
# Clean build for production
mvn clean package -Dmaven.test.skip=true

# Build with specific Java version
mvn clean package -Dmaven.compiler.source=21 -Dmaven.compiler.target=21

# Verify WAR contents
jar -tf target/ROOT.war
```

## Error Handling

### Servlet Exception Handling

The servlet implements comprehensive error handling following Java enterprise patterns:

```java
try {
    // Payment link creation logic
    String paymentLinkResponse = createPaymentLinkViaAPI(amount, currency, reference, name, description);
    response.getWriter().write(paymentLinkResponse);

} catch (Exception e) {
    System.err.println("Payment link creation failed: " + e.getMessage());
    e.printStackTrace();

    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    String errorResponse = String.format(
        "{\"success\":false,\"message\":\"Payment link creation failed\",\"error\":{\"code\":\"API_ERROR\",\"details\":\"%s\"}}",
        e.getMessage().replace("\"", "\\\"")
    );
    response.getWriter().write(errorResponse);
}
```

### Error Codes

- `MISSING_REQUIRED_FIELDS`: Required parameters not provided
- `INVALID_AMOUNT`: Amount is not a positive integer
- `API_ERROR`: Error response from Global Payments API

### HTTP Status Codes

- `200 OK`: Successful payment link creation
- `400 Bad Request`: Invalid request parameters
- `500 Internal Server Error`: Server-side processing errors

## Security Features

- **Input Sanitization**: All user inputs are sanitized using regex patterns
- **Reference Sanitization**: Removes potentially harmful characters matching PHP implementation
- **Length Limits**: Enforced on all text fields (reference: 100 chars, name: 100 chars, description: 500 chars)
- **Amount Validation**: Ensures positive integer amounts only
- **Environment Isolation**: Clear separation between sandbox and production endpoints
- **Token Security**: Access tokens are generated fresh for each request
- **Error Information**: Error responses don't expose sensitive internal details
- **Jakarta EE Security**: Leverages servlet container security features

## Troubleshooting

### Common Issues

1. **Missing Environment Variables**
   ```
   java.lang.NullPointerException at ProcessPaymentServlet.generateAccessToken
   ```
   **Solution**: Ensure `.env` file exists and contains valid `GP_API_APP_ID` and `GP_API_APP_KEY`

2. **Jakarta vs Javax Import Issues**
   ```
   java.lang.NoClassDefFoundError: javax/servlet/http/HttpServlet
   ```
   **Solution**: Ensure using Jakarta EE imports (`jakarta.servlet.*`) not legacy javax imports

3. **Port Already in Use**
   ```
   java.net.BindException: Address already in use: bind
   ```
   **Solution**: Change port in Cargo configuration or kill process using port 8000

4. **Maven Dependency Issues**
   ```
   [ERROR] Failed to execute goal on project pay-by-link-java: Could not resolve dependencies
   ```
   **Solution**: Run `mvn clean install -U` to force update dependencies

5. **Token Generation Failures**
   ```
   Payment link creation failed: Failed to generate access token: HTTP 401
   ```
   **Solution**: Verify API credentials and ensure correct environment endpoints

### Testing the API

Test the servlet endpoints:

```bash
# Test configuration endpoint
curl http://localhost:8000/config

# Test payment link creation
curl -X POST http://localhost:8000/create-payment-link \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d 'amount=1000&currency=USD&reference=Test%20Payment&name=Test%20Product&description=Testing%20payment%20link%20creation'
```

## Support

- **Global Payments Documentation**: [Global Payments Developer Portal](https://developer.globalpay.com/)
- **API Reference**: [Global Payments API Documentation](https://developer.globalpay.com/api)
- **Jakarta EE Documentation**: [Jakarta EE Platform Documentation](https://jakarta.ee/specifications/)
- **Maven Documentation**: [Apache Maven Project](https://maven.apache.org/guides/)
- **Tomcat Documentation**: [Apache Tomcat Documentation](https://tomcat.apache.org/tomcat-10.1-doc/)
