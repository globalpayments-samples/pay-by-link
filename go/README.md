# Global Payments Go Pay by Link Implementation

A high-performance Go implementation for creating and managing payment links using the Global Payments API. This native Go application provides a clean, dependency-minimal approach to generating secure payment links that can be shared with customers via email, SMS, or other channels.

## Features

- **Native Go HTTP Server**: Built using Go's standard `net/http` package with no external web framework
- **Minimal Dependencies**: Only requires `godotenv` for environment variable management
- **Direct API Integration**: Pure HTTP client implementation for both authentication and payment link creation
- **Type-Safe Structs**: Comprehensive Go structs with JSON tags for API communication
- **Multi-Currency Support**: Support for EUR, USD, GBP, and other currencies
- **Input Validation**: Comprehensive request validation and sanitization
- **Error Handling**: Go-idiomatic error handling with detailed error codes
- **Static File Serving**: Built-in static file serving from the current directory
- **JSON & Form Support**: Handles both JSON and form-encoded requests
- **Environment Configuration**: Flexible .env-based configuration for sandbox/production

## Requirements

- **Go**: 1.23.4 or later
- **Global Payments Account**: With Pay by Link API credentials

## Project Structure

```
go/
├── main.go                    # Main server implementation and API endpoints
├── go.mod                     # Go module configuration
├── go.sum                     # Dependency checksums
├── .env.sample                # Environment configuration template
└── static/                    # Static files directory (optional)
```

## Quick Start

### 1. Environment Setup

Copy the environment template and configure your credentials:

```bash
cp .env.sample .env
```

Update `.env` with your Global Payments API credentials:

```env
# GP API Keys for Global Payments Pay by Link
# Replace these sample values with your actual GP API credentials
GP_API_APP_ID=your_app_id_here
GP_API_APP_KEY=your_app_key_here

# Optional: Server port (defaults to 8000)
PORT=8000
```

### 2. Installation

Initialize Go modules and install dependencies:

```bash
# Initialize module (if not already done)
go mod init github.com/globalpayments/pay-by-link-go

# Install dependencies
go mod tidy
```

### 3. Running the Application

Run the application directly:

```bash
go run main.go
```

Or build and run:

```bash
go build -o paylink-server main.go
./paylink-server
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

Creates a new payment link with the specified parameters.

**Request Headers**:
```
Content-Type: application/json
# OR
Content-Type: application/x-www-form-urlencoded
```

**Request Parameters**:
- `amount` (string, required) - Amount in cents as string (e.g., "1000" = $10.00)
- `currency` (string, required) - Currency code (EUR, USD, GBP)
- `reference` (string, required) - Payment reference (max 100 chars)
- `name` (string, required) - Payment name/title (max 100 chars)
- `description` (string, required) - Payment description (max 500 chars)

**Example JSON Request**:
```bash
curl -X POST http://localhost:8000/create-payment-link \
  -H "Content-Type: application/json" \
  -d '{
    "amount": "2500",
    "currency": "USD",
    "reference": "Invoice #12345",
    "name": "Product Purchase",
    "description": "Payment for premium subscription"
  }'
```

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
    "details": "Missing required fields. Received: amount, currency"
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

API Error (400/500):
```json
{
  "success": false,
  "message": "Payment link creation failed",
  "error": {
    "code": "API_ERROR",
    "details": "Detailed error message from API"
  }
}
```

## Code Structure

### Main Components

#### HTTP Server Setup
```go
func main() {
    // Load environment variables
    err := godotenv.Load()
    if err != nil {
        log.Printf("Warning: Error loading .env file: %v", err)
    }

    // Set up routes
    http.Handle("/", http.FileServer(http.Dir("static")))
    http.Handle("/config", http.HandlerFunc(handleConfig))
    http.Handle("/create-payment-link", http.HandlerFunc(handleCreatePaymentLink))

    // Start server
    port := os.Getenv("PORT")
    if port == "" {
        port = "8000"
    }
    log.Fatal(http.ListenAndServe("0.0.0.0:"+port, nil))
}
```

#### Type-Safe Struct Definitions
```go
// PaymentLinkRequest represents the expected payment link creation request
type PaymentLinkRequest struct {
    Amount      string `json:"amount" form:"amount"`
    Currency    string `json:"currency" form:"currency"`
    Reference   string `json:"reference" form:"reference"`
    Name        string `json:"name" form:"name"`
    Description string `json:"description" form:"description"`
}

// PaymentLinkData represents the GP API payload structure
type PaymentLinkData struct {
    AccountName    string                    `json:"account_name"`
    Type          string                    `json:"type"`
    UsageMode     string                    `json:"usage_mode"`
    UsageLimit    int                       `json:"usage_limit"`
    Reference     string                    `json:"reference"`
    Name          string                    `json:"name"`
    Description   string                    `json:"description"`
    // ... other fields
}
```

#### Direct API Integration
The implementation uses pure HTTP client calls for both authentication and payment link creation:

```go
// Generate access token
func generateAccessToken() (*GPApiTokenResponse, error) {
    tokenRequest := GPApiTokenRequest{
        AppID:  os.Getenv("GP_API_APP_ID"),
        AppKey: os.Getenv("GP_API_APP_KEY"),
        Permissions: []string{"PMT_POST_Create", "PMT_POST_Detokenize"},
    }

    requestBody, err := json.Marshal(tokenRequest)
    if err != nil {
        return nil, fmt.Errorf("failed to marshal token request: %w", err)
    }

    client := &http.Client{Timeout: 30 * time.Second}
    req, err := http.NewRequest("POST", "https://apis.sandbox.globalpay.com/ucp/accesstoken",
        bytes.NewBuffer(requestBody))
    if err != nil {
        return nil, fmt.Errorf("failed to create token request: %w", err)
    }

    req.Header.Set("Content-Type", "application/json")
    req.Header.Set("X-GP-Version", "2021-03-22")

    // Execute request and handle response...
}
```

#### Input Validation and Sanitization

**Reference Sanitization**:
```go
func sanitizeReference(reference string) string {
    if reference == "" {
        return ""
    }
    // Remove any characters that aren't alphanumeric, spaces, hyphens, or hash
    reg := regexp.MustCompile(`[^\w\s\-#]`)
    sanitized := reg.ReplaceAllString(reference, "")
    // Limit length to 100 characters
    if len(sanitized) > 100 {
        return sanitized[:100]
    }
    return sanitized
}
```

**Required Field Validation**:
```go
requiredFields := []string{"amount", "currency", "reference", "name", "description"}
receivedFields := []string{}

// Check which fields were received
if req.Amount != "" { receivedFields = append(receivedFields, "amount") }
if req.Currency != "" { receivedFields = append(receivedFields, "currency") }
// ... check other fields

// Validate all required fields are present
missingFields := []string{}
for _, field := range requiredFields {
    found := false
    for _, received := range receivedFields {
        if field == received {
            found = true
            break
        }
    }
    if !found {
        missingFields = append(missingFields, field)
    }
}
```

## Dependencies

### Core Dependencies

The Go implementation has minimal external dependencies:

- **github.com/joho/godotenv** (v1.5.1): Environment variable management from .env files

### Standard Library Usage

The application leverages Go's robust standard library:

- **net/http**: HTTP server and client functionality
- **encoding/json**: JSON marshaling and unmarshaling
- **regexp**: Input sanitization with regular expressions
- **time**: Timestamp handling and HTTP client timeouts
- **os**: Environment variable access
- **log**: Application logging
- **io**: Request/response body handling
- **bytes**: HTTP request body construction
- **strings**: String manipulation and validation
- **strconv**: String to integer conversion
- **fmt**: Formatted string operations and error handling

### Go Module Configuration

```go
module github.com/globalpayments/pay-by-link-go

go 1.23.4

require github.com/joho/godotenv v1.5.1
```

## Implementation Details

### Payment Link Configuration

Payment links are created with the following settings:

- **Type**: PAYMENT
- **Usage Mode**: SINGLE (one-time use)
- **Usage Limit**: 1
- **Allowed Payment Methods**: CARD
- **Channel**: CNP (Card Not Present)
- **Country**: GB (United Kingdom)
- **Expiration**: 10 days from creation
- **Shipping**: YES with $0 shipping amount

### Default URLs Configuration
```go
payByLinkData := PaymentLinkData{
    // ... other configuration
    Notifications: PaymentLinkNotifications{
        ReturnURL: "https://www.example.com/returnUrl",
        StatusURL: "https://www.example.com/statusUrl",
        CancelURL: "https://www.example.com/returnUrl",
    },
}
```

### Error Handling

The application implements Go-idiomatic error handling with specific error codes:

- `MISSING_REQUIRED_FIELDS`: Required parameters not provided
- `INVALID_AMOUNT`: Amount is not a positive integer
- `INVALID_JSON`: JSON parsing failed
- `FORM_PARSE_ERROR`: Form data parsing failed
- `TOKEN_GENERATION_ERROR`: Failed to generate access token
- `API_ERROR`: Error response from Global Payments API
- `INVALID_RESPONSE`: API response missing expected data

### HTTP Client Configuration

```go
client := &http.Client{Timeout: 30 * time.Second}
```

All API requests use a 30-second timeout to prevent hanging connections.

## Development vs Production

### Development Configuration

```go
// Development uses sandbox endpoints
const sandboxTokenURL = "https://apis.sandbox.globalpay.com/ucp/accesstoken"
const sandboxLinksURL = "https://apis.sandbox.globalpay.com/ucp/links"
```

### Production Configuration

For production deployment:

1. Update environment variables:
   ```env
   GP_API_APP_ID=your_production_app_id
   GP_API_APP_KEY=your_production_app_key
   ```

2. Update API endpoints in code:
   ```go
   const prodTokenURL = "https://apis.globalpay.com/ucp/accesstoken"
   const prodLinksURL = "https://apis.globalpay.com/ucp/links"
   ```

3. Update configuration response:
   ```go
   response := Response{
       Success: true,
       Data: Config{
           Environment: "production", // Changed from "sandbox"
           // ... other config
       },
   }
   ```

## Security Features

- **Input Sanitization**: All user inputs are sanitized and validated
- **Reference Sanitization**: Removes potentially harmful characters using regex
- **Length Limits**: Enforced on all text fields (reference: 100 chars, name: 100 chars, description: 500 chars)
- **Amount Validation**: Ensures positive integer amounts only using `strconv.Atoi`
- **Environment Isolation**: Clear separation between sandbox and production endpoints
- **Token Security**: Access tokens are generated fresh for each request
- **Timeout Protection**: 30-second HTTP client timeouts prevent hanging requests
- **Error Information**: Error responses don't expose sensitive internal details
- **Explicit Error Returns**: Go's explicit error handling prevents silent failures

## Building and Deployment

### Local Development

```bash
# Run directly
go run main.go

# Run with custom port
PORT=3000 go run main.go
```

### Building for Production

```bash
# Build for current platform
go build -o paylink-server main.go

# Build for Linux (common for deployment)
GOOS=linux GOARCH=amd64 go build -o paylink-server-linux main.go

# Build with optimizations
go build -ldflags="-w -s" -o paylink-server main.go
```

### Docker Deployment

Create a `Dockerfile`:

```dockerfile
FROM golang:1.23.4-alpine AS builder
WORKDIR /app
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN go build -ldflags="-w -s" -o paylink-server main.go

FROM alpine:latest
RUN apk --no-cache add ca-certificates
WORKDIR /root/
COPY --from=builder /app/paylink-server .
COPY --from=builder /app/.env .
EXPOSE 8000
CMD ["./paylink-server"]
```

Build and run:

```bash
docker build -t paylink-go .
docker run -p 8000:8000 --env-file .env paylink-go
```

## Troubleshooting

### Common Issues

1. **Missing Environment Variables**
   ```
   Fatal error: Missing required environment variables: GP_API_APP_ID and GP_API_APP_KEY
   ```
   **Solution**: Ensure `.env` file exists and contains valid `GP_API_APP_ID` and `GP_API_APP_KEY`

2. **Port Already in Use**
   ```
   Fatal error: listen tcp :8000: bind: address already in use
   ```
   **Solution**: Change the port by setting `PORT` environment variable or kill the process using port 8000

3. **Module Import Issues**
   ```
   go: cannot find module providing package github.com/joho/godotenv
   ```
   **Solution**: Run `go mod tidy` to download dependencies

4. **JSON Parsing Errors**
   ```
   Error parsing JSON request body
   ```
   **Solution**: Ensure request has proper `Content-Type: application/json` header and valid JSON body

5. **Token Generation Failures**
   ```
   Token request failed with status 401: unauthorized
   ```
   **Solution**: Verify API credentials are correct for the target environment (sandbox vs production)

### Debug Mode

The application provides detailed console logging:

```bash
go run main.go
# Server will output:
# - Configured GP API App ID
# - Received POST data details
# - Payment link creation attempts
# - API request status
# - Error conditions with stack traces
```

### Testing the API

Test the endpoints using curl:

```bash
# Test configuration endpoint
curl http://localhost:8000/config

# Test payment link creation with JSON
curl -X POST http://localhost:8000/create-payment-link \
  -H "Content-Type: application/json" \
  -d '{
    "amount": "1000",
    "currency": "USD",
    "reference": "Test Payment",
    "name": "Test Product",
    "description": "Testing payment link creation"
  }'

# Test payment link creation with form data
curl -X POST http://localhost:8000/create-payment-link \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d 'amount=1000&currency=USD&reference=Test%20Payment&name=Test%20Product&description=Testing%20payment%20link%20creation'
```

### Performance Testing

Test server performance with multiple concurrent requests:

```bash
# Install hey (HTTP load testing tool)
go install github.com/rakyll/hey@latest

# Test with 100 concurrent requests
hey -n 1000 -c 100 -m POST \
  -H "Content-Type: application/json" \
  -d '{"amount":"1000","currency":"USD","reference":"Load Test","name":"Performance Test","description":"Load testing the payment link API"}' \
  http://localhost:8000/create-payment-link
```

## Go-Specific Features

### Memory Efficiency

- **Minimal Dependencies**: Only one external dependency reduces memory footprint
- **Struct Reuse**: Type definitions are reused across requests
- **Efficient JSON Handling**: Uses Go's optimized `encoding/json` package
- **Connection Pooling**: HTTP client reuses connections automatically

### Concurrency

Go's goroutine model handles concurrent requests efficiently:

```go
// Each HTTP request is handled in its own goroutine automatically
http.Handle("/create-payment-link", http.HandlerFunc(handleCreatePaymentLink))
```

The server can handle thousands of concurrent requests with minimal resource usage.

### Type Safety

Strong typing prevents runtime errors:

```go
// Compile-time type checking
type PaymentLinkRequest struct {
    Amount   string `json:"amount"`   // Explicit string type
    Currency string `json:"currency"` // Prevents type confusion
}

// Type-safe conversions with error handling
amount, err := strconv.Atoi(req.Amount)
if err != nil || amount <= 0 {
    // Handle conversion error explicitly
}
```

## Customization

### Adding Payment Methods

To support additional payment methods, update the allowed payment methods:

```go
payByLinkData := PaymentLinkData{
    // ... other configuration
    Transactions: PaymentLinkTransactions{
        AllowedPaymentMethods: []string{"CARD", "APM"}, // Add alternative payment methods
        // ... other transaction settings
    },
}
```

### Custom Return URLs

Configure custom URLs for your application:

```go
payByLinkData := PaymentLinkData{
    // ... other configuration
    Notifications: PaymentLinkNotifications{
        ReturnURL: "https://yourdomain.com/payment/success",
        StatusURL: "https://yourdomain.com/webhook/payment-status",
        CancelURL: "https://yourdomain.com/payment/cancel",
    },
}
```

### Modifying Link Expiration

Change the expiration period:

```go
// Set expiration to 30 days instead of 10
expirationDate := time.Now().Add(30 * 24 * time.Hour).Format("2006-01-02 15:04:05")

payByLinkData := PaymentLinkData{
    // ... other configuration
    ExpirationDate: expirationDate,
}
```

### Adding Request Logging Middleware

Implement custom request logging:

```go
func loggingMiddleware(next http.Handler) http.Handler {
    return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
        start := time.Now()
        log.Printf("Started %s %s", r.Method, r.URL.Path)

        next.ServeHTTP(w, r)

        duration := time.Since(start)
        log.Printf("Completed %s %s in %v", r.Method, r.URL.Path, duration)
    })
}

func main() {
    // ... setup code

    // Wrap handlers with logging middleware
    http.Handle("/create-payment-link",
        loggingMiddleware(http.HandlerFunc(handleCreatePaymentLink)))
}
```

### Environment-Based Configuration

Create environment-specific configurations:

```go
type Environment struct {
    TokenURL string
    LinksURL string
    Name     string
}

func getEnvironment() Environment {
    if os.Getenv("GP_API_ENVIRONMENT") == "production" {
        return Environment{
            TokenURL: "https://apis.globalpay.com/ucp/accesstoken",
            LinksURL: "https://apis.globalpay.com/ucp/links",
            Name:     "production",
        }
    }
    return Environment{
        TokenURL: "https://apis.sandbox.globalpay.com/ucp/accesstoken",
        LinksURL: "https://apis.sandbox.globalpay.com/ucp/links",
        Name:     "sandbox",
    }
}
```

## Support

- **Documentation**: [Global Payments Developer Portal](https://developer.globalpay.com/)
- **API Reference**: [Global Payments API Documentation](https://developer.globalpay.com/api)
- **Go Documentation**: [Go Language Documentation](https://golang.org/doc/)
- **HTTP Package**: [net/http Package Documentation](https://pkg.go.dev/net/http)
