# Global Payments .NET Pay by Link Implementation

A modern ASP.NET Core 9.0 implementation for creating and managing payment links using the Global Payments API. This lightweight application leverages Minimal APIs for high-performance payment link generation with direct HTTP API integration, comprehensive validation, and production-ready error handling.

## Features

- **ASP.NET Core 9.0**: Built on the latest .NET framework with Minimal APIs for optimal performance
- **Minimal API Architecture**: Lightweight, high-performance endpoints without MVC overhead
- **Direct HTTP Integration**: Native HttpClient with HttpRequestMessage for direct GP API communication
- **System.Text.Json**: Built-in JSON serialization with custom attribute mapping
- **Environment Configuration**: .env file support with DotEnv.Net for flexible deployment
- **Input Validation & Sanitization**: Comprehensive request validation with C# pattern matching
- **Multi-Currency Support**: Support for EUR, USD, GBP, and other Global Payments currencies
- **Nullable Reference Types**: Enhanced type safety with C# nullable reference type annotations
- **Static File Serving**: Built-in static content serving with UseDefaultFiles() and UseStaticFiles()
- **Cross-Platform Deployment**: Runs on Windows, Linux, macOS, and containerized environments
- **Production-Ready Error Handling**: Structured error responses with proper HTTP status codes

## Requirements

- **.NET 9.0+**: Latest .NET runtime and SDK
- **Global Payments Account**: With Pay by Link API credentials (GP API App ID and Key)
- **Development Environment**: Visual Studio 2022, VS Code, or JetBrains Rider (optional)

## Project Structure

```
dotnet/
├── Program.cs                    # Main application with Minimal API configuration
├── dotnet.csproj                # .NET project file and NuGet dependencies
├── index.html                   # Payment form interface
├── .env.sample                  # Environment configuration template
├── appsettings.json            # Application configuration
├── Dockerfile                  # Container deployment configuration
├── run.sh                      # Convenience script for local development
└── wwwroot/                    # Static web assets
    └── (additional static files)
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

Install .NET dependencies and verify framework version:

```bash
# Verify .NET 9.0+ is installed
dotnet --version

# Restore NuGet packages
dotnet restore

# Build the application
dotnet build
```

### 3. Running the Application

#### Development Mode

Run the ASP.NET Core application locally:

```bash
# Start the development server (default port 8000)
dotnet run

# Or using the convenience script
./run.sh

# The server will be available at http://localhost:8000
```

#### Custom Port Configuration

```bash
# Run on custom port using environment variable
PORT=5000 dotnet run

# Or set in .env file
echo "PORT=5000" >> .env
dotnet run
```

#### Production Build

Create optimized production build:

```bash
# Publish for production
dotnet publish -c Release -o ./publish

# Run published application
cd publish
./dotnet

# Or build for specific runtime (self-contained)
dotnet publish -c Release -r linux-x64 --self-contained
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

API Error (400):
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

### Main Application Architecture

The `Program.cs` file contains the entire ASP.NET Core Minimal API application:

```csharp
namespace PayByLinkSample;

/// <summary>
/// Pay by Link Processing Application
/// </summary>
public class Program
{
    private static readonly HttpClient HttpClient = new();

    public static void Main(string[] args)
    {
        // Load environment variables from .env file
        DotEnv.Load();

        var builder = WebApplication.CreateBuilder(args);
        var app = builder.Build();

        // Configure static file serving
        app.UseDefaultFiles();
        app.UseStaticFiles();

        ConfigureEndpoints(app);

        var port = System.Environment.GetEnvironmentVariable("PORT") ?? "8000";
        app.Urls.Add($"http://0.0.0.0:{port}");

        app.Run();
    }
}
```

### Minimal API Endpoint Configuration

```csharp
private static void ConfigureEndpoints(WebApplication app)
{
    // Configuration endpoint
    app.MapGet("/config", () => Results.Ok(new
    {
        success = true,
        data = new {
            environment = "sandbox",
            supportedCurrencies = new[] { "EUR", "USD", "GBP" },
            supportedPaymentMethods = new[] { "CARD" }
        }
    }));

    // Payment link creation endpoint
    app.MapPost("/create-payment-link", async (HttpContext context) =>
    {
        // Form data processing and payment link creation
    });
}
```

### Direct HTTP API Integration

The implementation uses native `HttpClient` and `HttpRequestMessage` for direct API calls:

```csharp
private static async Task<GpApiTokenResponse?> GenerateAccessToken()
{
    var appId = System.Environment.GetEnvironmentVariable("GP_API_APP_ID");
    var appKey = System.Environment.GetEnvironmentVariable("GP_API_APP_KEY");

    var tokenRequest = new GpApiTokenRequest
    {
        AppId = appId,
        AppKey = appKey,
        Permissions = ["PMT_POST_Create", "PMT_POST_Detokenize"]
    };

    var requestJson = JsonSerializer.Serialize(tokenRequest);
    var content = new StringContent(requestJson, System.Text.Encoding.UTF8, "application/json");

    using var request = new HttpRequestMessage(HttpMethod.Post,
        "https://apis.sandbox.globalpay.com/ucp/accesstoken");
    request.Content = content;
    request.Headers.Add("X-GP-Version", "2021-03-22");

    var response = await HttpClient.SendAsync(request);
    var responseContent = await response.Content.ReadAsStringAsync();

    return JsonSerializer.Deserialize<GpApiTokenResponse>(responseContent);
}
```

### Form Data Processing

ASP.NET Core form data handling with validation:

```csharp
// Parse form data from the request
var form = await context.Request.ReadFormAsync();

// Validate required fields
string[] requiredFields = ["amount", "currency", "reference", "name", "description"];
var missingFields = requiredFields.Where(field => string.IsNullOrEmpty(form[field])).ToList();

if (missingFields.Any())
{
    return Results.BadRequest(new {
        success = false,
        message = "Payment link creation failed",
        error = new {
            code = "MISSING_REQUIRED_FIELDS",
            details = $"Missing required fields. Received: {string.Join(", ", form.Keys)}"
        }
    });
}
```

### Input Validation and Sanitization

**Reference Sanitization** (matching other implementations):
```csharp
/// <summary>
/// Sanitizes reference string by removing potentially harmful characters.
/// </summary>
private static string SanitizeReference(string? reference)
{
    if (string.IsNullOrEmpty(reference)) return string.Empty;

    // Remove any characters that aren't alphanumeric, spaces, hyphens, or hash
    var sanitized = Regex.Replace(reference, @"[^\w\s\-#]", "");

    // Limit length to 100 characters
    return sanitized.Length > 100 ? sanitized[..100] : sanitized;
}
```

**Amount Validation**:
```csharp
// Parse and validate amount
if (!int.TryParse(form["amount"], out var amount) || amount <= 0)
{
    return Results.BadRequest(new {
        success = false,
        message = "Payment link creation failed",
        error = new {
            code = "INVALID_AMOUNT",
            details = "Invalid amount"
        }
    });
}
```

### JSON Serialization with System.Text.Json

Model classes with `JsonPropertyName` attributes for snake_case mapping:

```csharp
public class GpApiTokenRequest
{
    [JsonPropertyName("app_id")]
    public string AppId { get; set; } = "";

    [JsonPropertyName("app_key")]
    public string AppKey { get; set; } = "";

    [JsonPropertyName("permissions")]
    public string[] Permissions { get; set; } = [];
}

public class PaymentLinkData
{
    [JsonPropertyName("account_name")]
    public string AccountName { get; set; } = "";

    [JsonPropertyName("type")]
    public string Type { get; set; } = "";

    [JsonPropertyName("usage_mode")]
    public string UsageMode { get; set; } = "";

    // ... additional properties with JsonPropertyName attributes
}
```

## Dependencies

### Core NuGet Packages

The project uses minimal dependencies for optimal performance:

```xml
<PackageReference Include="GlobalPayments.Api" Version="9.0.16" />
<PackageReference Include="DotEnv.Net" Version="3.2.1" />
```

### Project Configuration

```xml
<Project Sdk="Microsoft.NET.Sdk.Web">
  <PropertyGroup>
    <TargetFramework>net9.0</TargetFramework>
    <Nullable>enable</Nullable>
    <ImplicitUsings>enable</ImplicitUsings>
  </PropertyGroup>
</Project>
```

**Key Features**:
- **GlobalPayments.Api**: Official Global Payments SDK (referenced but using direct API calls)
- **DotEnv.Net**: Environment variable loading from .env files
- **Nullable Reference Types**: Enhanced compile-time null safety
- **Implicit Usings**: Automatic using statements for common namespaces

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

```csharp
var payByLinkData = new PaymentLinkData
{
    AccountName = accountName,
    Type = "PAYMENT",
    UsageMode = "SINGLE",
    UsageLimit = 1,
    Reference = reference,
    Name = name,
    Description = description,
    Shippable = "YES",
    ShippingAmount = 0,
    ExpirationDate = expirationDate,
    Transactions = new PaymentLinkTransactions
    {
        AllowedPaymentMethods = ["CARD"],
        Channel = "CNP",
        Country = "GB",
        Amount = amount,
        Currency = currency
    },
    Notifications = new PaymentLinkNotifications
    {
        ReturnUrl = "https://www.example.com/returnUrl",
        StatusUrl = "https://www.example.com/statusUrl",
        CancelUrl = "https://www.example.com/returnUrl"
    }
};
```

### Expiration Date Handling

```csharp
var expirationDate = DateTime.Now.AddDays(10).ToString("yyyy-MM-dd HH:mm:ss");
```

## Development vs Production

### Development Configuration

The application defaults to sandbox environment with development-friendly settings:

**Sandbox API Endpoints**:
- Token URL: `https://apis.sandbox.globalpay.com/ucp/accesstoken`
- Links URL: `https://apis.sandbox.globalpay.com/ucp/links`

**Development Features**:
- Console logging for debugging
- Detailed error messages
- .env file loading for local development
- Hot reload support with `dotnet watch run`

### Production Configuration

For production deployment:

1. **Update Environment Variables**:
   ```env
   GP_API_APP_ID=your_production_app_id
   GP_API_APP_KEY=your_production_app_key
   GP_API_ENVIRONMENT=production
   ```

2. **Update API Endpoints** (modify Program.cs):
   ```csharp
   // Change URLs to production endpoints
   using var request = new HttpRequestMessage(HttpMethod.Post,
       "https://apis.globalpay.com/ucp/accesstoken");

   using var linkRequest = new HttpRequestMessage(HttpMethod.Post,
       "https://apis.globalpay.com/ucp/links");
   ```

3. **Production Build Optimization**:
   ```bash
   # Create optimized release build
   dotnet publish -c Release -o ./publish --self-contained false

   # Or create self-contained deployment
   dotnet publish -c Release -r linux-x64 --self-contained true
   ```

### Docker Deployment

The included Dockerfile supports containerized deployment:

```bash
# Build Docker image
docker build -t dotnet-pay-by-link .

# Run container
docker run -p 8000:8000 --env-file .env dotnet-pay-by-link
```

## Error Handling

### ASP.NET Core Exception Handling

The application implements comprehensive error handling using try-catch blocks with structured error responses:

```csharp
try
{
    // Payment link creation logic
    var linkResponse = await CreatePaymentLink(payByLinkData, tokenResponse.Token);

    return Results.Ok(new
    {
        success = true,
        message = $"Payment link created successfully! Link ID: {linkResponse.Id}",
        data = new {
            paymentLink = linkResponse.Url,
            linkId = linkResponse.Id,
            reference = reference,
            amount = amount,
            currency = currency
        }
    });
}
catch (Exception ex)
{
    Console.WriteLine($"Payment link creation failed: {ex.Message}");

    // Determine error code based on exception type/message
    string errorCode = ex.Message.Contains("Failed to generate access token")
        ? "TOKEN_GENERATION_ERROR"
        : "API_ERROR";

    return Results.BadRequest(new {
        success = false,
        message = "Payment link creation failed",
        error = new {
            code = errorCode,
            details = ex.Message
        }
    });
}
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
- `500 Internal Server Error`: Server-side processing errors (mapped to BadRequest in Minimal API)

## Security Features

- **Input Sanitization**: All user inputs sanitized using regex patterns and length limits
- **Reference Sanitization**: Removes potentially harmful characters (matches other implementations)
- **Length Limits**: Enforced on all text fields (reference: 100 chars, name: 100 chars, description: 500 chars)
- **Amount Validation**: Ensures positive integer amounts only with TryParse validation
- **Environment Isolation**: Clear separation between sandbox and production endpoints
- **Token Security**: Access tokens generated fresh for each request with secure disposal
- **Nullable Reference Types**: Compile-time null safety to prevent null reference exceptions
- **Error Information**: Error responses don't expose sensitive internal system details
- **HTTPS Enforcement**: Production deployments should enforce HTTPS-only communication

## Troubleshooting

### Common Issues

1. **Missing Environment Variables**
   ```
   System.InvalidOperationException: Missing GP_API_APP_ID or GP_API_APP_KEY environment variables
   ```
   **Solution**: Ensure `.env` file exists and contains valid `GP_API_APP_ID` and `GP_API_APP_KEY`

2. **.NET Version Issues**
   ```
   error NETSDK1045: The current .NET SDK does not support targeting .NET 9.0
   ```
   **Solution**: Install .NET 9.0 SDK from https://dotnet.microsoft.com/download

3. **Port Already in Use**
   ```
   System.IO.IOException: Failed to bind to address http://0.0.0.0:8000
   ```
   **Solution**: Change port using `PORT=5000 dotnet run` or kill process using port 8000

4. **NuGet Package Restore Issues**
   ```
   error NU1101: Unable to find package DotEnv.Net
   ```
   **Solution**: Run `dotnet restore --force` to restore all NuGet packages

5. **Token Generation Failures**
   ```
   Payment link creation failed: Token request failed with status Unauthorized
   ```
   **Solution**: Verify API credentials are correct and match the environment (sandbox/production)

6. **Form Data Parsing Issues**
   ```
   System.InvalidOperationException: Request content type is not supported
   ```
   **Solution**: Ensure requests use `Content-Type: application/x-www-form-urlencoded`

### Testing the API

Test the ASP.NET Core endpoints:

```bash
# Test configuration endpoint
curl http://localhost:8000/config

# Test payment link creation
curl -X POST http://localhost:8000/create-payment-link \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d 'amount=1000&currency=USD&reference=Test%20Payment&name=Test%20Product&description=Testing%20payment%20link%20creation%20with%20ASP.NET%20Core'

# Test with PowerShell (Windows)
Invoke-RestMethod -Uri "http://localhost:8000/config" -Method Get
```

### Development Tools

**Visual Studio / VS Code Integration**:
```bash
# Install C# Dev Kit extension for VS Code
code --install-extension ms-dotnettools.csharp

# Run with debugging
dotnet run --configuration Debug

# Watch for file changes (hot reload)
dotnet watch run
```

**Performance Testing**:
```bash
# Install dotnet tool for load testing (optional)
dotnet tool install -g NBomber.DotNet

# Basic performance test
dotnet run --configuration Release
```

## Support

- **Global Payments Documentation**: [Global Payments Developer Portal](https://developer.globalpay.com/)
- **API Reference**: [Global Payments API Documentation](https://developer.globalpay.com/api)
- **.NET Documentation**: [Microsoft .NET Documentation](https://docs.microsoft.com/en-us/dotnet/)
- **ASP.NET Core Documentation**: [ASP.NET Core Documentation](https://docs.microsoft.com/en-us/aspnet/core/)
- **Minimal APIs Guide**: [ASP.NET Core Minimal APIs](https://docs.microsoft.com/en-us/aspnet/core/fundamentals/minimal-apis)