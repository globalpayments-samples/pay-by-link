using dotenv.net;
using System.Net;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Text.RegularExpressions;

namespace PayByLinkSample;

/// <summary>
/// Pay by Link Processing Application
///
/// This application demonstrates payment link creation using the Global Payments API.
/// It provides endpoints for payment link creation, handling
/// form data to create secure payment links that can be shared with customers.
/// </summary>
public class Program
{
    private static readonly HttpClient HttpClient = new HttpClient(new HttpClientHandler()
    {
        AutomaticDecompression = DecompressionMethods.GZip | DecompressionMethods.Deflate
    });

    public static void Main(string[] args)
    {
        // Load environment variables from .env file
        DotEnv.Load();

        var builder = WebApplication.CreateBuilder(args);

        var app = builder.Build();

        // Enable static file serving
        app.UseDefaultFiles();
        app.UseStaticFiles();

        ConfigureEndpoints(app);

        var port = System.Environment.GetEnvironmentVariable("PORT") ?? "8000";
        app.Urls.Add($"http://0.0.0.0:{port}");

        app.Run();
    }

    /// <summary>
    /// Configures the application's HTTP endpoints.
    /// </summary>
    /// <param name="app">The web application instance</param>
    private static void ConfigureEndpoints(WebApplication app)
    {
        // Set up HTTP endpoints
        app.MapGet("/config", () => Results.Ok(new
        {
            success = true,
            data = new {
                environment = "sandbox",
                supportedCurrencies = new[] { "EUR", "USD", "GBP" },
                supportedPaymentMethods = new[] { "CARD" }
            }
        }));

        ConfigurePaymentLinkEndpoint(app);
    }

    /// <summary>
    /// Sanitizes reference string by removing potentially harmful characters.
    /// </summary>
    /// <param name="reference">The reference to sanitize. Can be null.</param>
    /// <returns>
    /// A sanitized reference containing only alphanumeric characters, spaces, hyphens, and hash,
    /// limited to 100 characters. Returns empty string if input is null or empty.
    /// </returns>
    private static string SanitizeReference(string? reference)
    {
        if (string.IsNullOrEmpty(reference)) return string.Empty;

        // Remove any characters that aren't alphanumeric, spaces, hyphens, or hash
        var sanitized = Regex.Replace(reference, @"[^\w\s\-#]", "");

        // Limit length to 100 characters
        return sanitized.Length > 100 ? sanitized[..100] : sanitized;
    }

    /// <summary>
    /// Generates a secret hash using SHA512 for GP API authentication.
    /// The secret is created as SHA512(NONCE + APP-KEY).
    /// </summary>
    /// <param name="nonce">The nonce value</param>
    /// <param name="appKey">The application key</param>
    /// <returns>SHA512 hash as lowercase hex string</returns>
    private static string GenerateSecret(string nonce, string appKey)
    {
        var data = Encoding.UTF8.GetBytes(nonce + appKey);

        using var sha512 = SHA512.Create();
        var hash = sha512.ComputeHash(data);

        var sb = new StringBuilder(128);
        foreach (var b in hash)
        {
            sb.Append(b.ToString("X2"));
        }

        return sb.ToString().ToLower();
    }

    /// <summary>
    /// Generates an access token for GP API using app credentials.
    /// </summary>
    /// <returns>GP API token response</returns>
    private static async Task<GpApiTokenResponse?> GenerateAccessToken()
    {
        var appId = System.Environment.GetEnvironmentVariable("GP_API_APP_ID");
        var appKey = System.Environment.GetEnvironmentVariable("GP_API_APP_KEY");

        if (string.IsNullOrEmpty(appId) || string.IsNullOrEmpty(appKey))
        {
            throw new InvalidOperationException("Missing GP_API_APP_ID or GP_API_APP_KEY environment variables");
        }

        var nonce = DateTime.UtcNow.ToString("MM/dd/yyyy hh:mm:ss.fff tt");

        var tokenRequest = new GpApiTokenRequest
        {
            AppId = appId,
            Nonce = nonce,
            GrantType = "client_credentials",
            Secret = GenerateSecret(nonce, appKey)
        };

        var requestJson = JsonSerializer.Serialize(tokenRequest);
        var content = new StringContent(requestJson, System.Text.Encoding.UTF8, "application/json");

        using var request = new HttpRequestMessage(HttpMethod.Post, "https://apis.sandbox.globalpay.com/ucp/accesstoken");
        request.Content = content;
        request.Headers.Add("X-GP-Api-Key", appKey);
        request.Headers.Add("X-GP-Version", "2021-03-22");
        request.Headers.Add("Accept", "application/json");
        request.Headers.Add("User-Agent", "PayByLink-DotNet/1.0");

        var response = await HttpClient.SendAsync(request);
        var responseContent = await response.Content.ReadAsStringAsync();

        if (!response.IsSuccessStatusCode)
        {
            throw new InvalidOperationException($"Token request failed with status {response.StatusCode}: {responseContent}");
        }

        return JsonSerializer.Deserialize<GpApiTokenResponse>(responseContent);
    }

    /// <summary>
    /// Creates a payment link via direct GP API call.
    /// </summary>
    /// <param name="paymentLinkData">Payment link data</param>
    /// <param name="accessToken">Access token for authentication</param>
    /// <returns>Payment link response</returns>
    private static async Task<GpApiLinkResponse?> CreatePaymentLink(PaymentLinkData paymentLinkData, string accessToken)
    {
        var requestJson = JsonSerializer.Serialize(paymentLinkData, new JsonSerializerOptions
        {
            PropertyNamingPolicy = JsonNamingPolicy.SnakeCaseLower
        });

        var content = new StringContent(requestJson, System.Text.Encoding.UTF8, "application/json");

        using var request = new HttpRequestMessage(HttpMethod.Post, "https://apis.sandbox.globalpay.com/ucp/links");
        request.Content = content;
        request.Headers.Add("Authorization", $"Bearer {accessToken}");
        request.Headers.Add("X-GP-Version", "2021-03-22");
        request.Headers.Add("Accept", "application/json");
        request.Headers.Add("User-Agent", "PayByLink-DotNet/1.0");

        var response = await HttpClient.SendAsync(request);
        var responseContent = await response.Content.ReadAsStringAsync();

        if (!response.IsSuccessStatusCode)
        {
            // Try to parse error response for better error details
            string errorMsg;
            try
            {
                using var errorDoc = JsonDocument.Parse(responseContent);
                if (errorDoc.RootElement.TryGetProperty("error_description", out var errorDesc))
                {
                    errorMsg = errorDesc.GetString() ?? responseContent;
                }
                else if (errorDoc.RootElement.TryGetProperty("message", out var message))
                {
                    errorMsg = message.GetString() ?? responseContent;
                }
                else
                {
                    errorMsg = responseContent;
                }
            }
            catch
            {
                errorMsg = responseContent;
            }

            throw new InvalidOperationException($"Payment link creation failed with status {response.StatusCode}: {errorMsg}");
        }

        return JsonSerializer.Deserialize<GpApiLinkResponse>(responseContent);
    }

    /// <summary>
    /// Sets up the payment link creation endpoint.
    /// </summary>
    /// <param name="app">The web application instance</param>
    private static void ConfigurePaymentLinkEndpoint(WebApplication app)
    {
        app.MapPost("/create-payment-link", async (HttpContext context) =>
        {
            try
            {
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

                // Sanitize and prepare data
                var reference = SanitizeReference(form["reference"]);
                var name = form["name"].ToString().Trim();
                if (name.Length > 100) name = name[..100];

                var description = form["description"].ToString().Trim();
                if (description.Length > 500) description = description[..500];

                var currency = form["currency"].ToString().Trim().ToUpper();

                // Generate access token
                var tokenResponse = await GenerateAccessToken();
                if (tokenResponse == null)
                {
                    return Results.BadRequest(new {
                        success = false,
                        message = "Payment link creation failed",
                        error = new {
                            code = "TOKEN_GENERATION_ERROR",
                            details = "Failed to generate access token"
                        }
                    });
                }

                // Set account name from token response or default to "paylink"
                var accountName = !string.IsNullOrEmpty(tokenResponse.TransactionProcessingAccountName)
                    ? tokenResponse.TransactionProcessingAccountName
                    : "paylink";

                // Create PayByLink data object
                var expirationDate = DateTime.Now.AddDays(10).ToString("yyyy-MM-dd HH:mm:ss");

                var payByLinkData = new PaymentLinkData
                {
                    AccountName = accountName,
                    Type = "PAYMENT",  // PayByLinkType::PAYMENT
                    UsageMode = "SINGLE",   // PaymentMethodUsageMode::SINGLE
                    UsageLimit = 1,          // usageLimit = 1
                    Reference = reference,
                    Name = name,
                    Description = description,
                    Shippable = "YES",
                    ShippingAmount = 0,         // shippingAmount = 0
                    ExpirationDate = expirationDate, // +10 days
                    Transactions = new PaymentLinkTransactions
                    {
                        AllowedPaymentMethods = ["CARD"], // allowedPaymentMethods = [PaymentMethodName::CARD]
                        Channel = "CNP",             // Card Not Present
                        Country = "GB",
                        Amount = amount,            // Amount in cents
                        Currency = currency
                    },
                    Notifications = new PaymentLinkNotifications
                    {
                        ReturnUrl = "https://www.example.com/returnUrl",  // returnUrl
                        StatusUrl = "https://www.example.com/statusUrl",  // statusUpdateUrl
                        CancelUrl = "https://www.example.com/returnUrl"   // cancelUrl
                    }
                };

                // Add merchant_id if available
                if (!string.IsNullOrEmpty(tokenResponse.MerchantId))
                {
                    payByLinkData.MerchantId = tokenResponse.MerchantId;
                }

                // Create payment link via GP API
                var linkResponse = await CreatePaymentLink(payByLinkData, tokenResponse.Token);
                if (linkResponse == null || string.IsNullOrEmpty(linkResponse.Url))
                {
                    return Results.BadRequest(new {
                        success = false,
                        message = "Payment link creation failed",
                        error = new {
                            code = "INVALID_RESPONSE",
                            details = "No payment link URL in response"
                        }
                    });
                }

                // Return success response
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
                // Determine error code based on exception type/message
                string errorCode = "UNKNOWN_ERROR";
                if (ex.Message.Contains("Failed to generate access token") || ex.Message.Contains("Token request failed"))
                {
                    errorCode = "TOKEN_GENERATION_ERROR";
                }
                else if (ex.Message.Contains("Payment link creation failed") || ex.Message.Contains("Invalid response"))
                {
                    errorCode = "API_ERROR";
                }

                return Results.BadRequest(new {
                    success = false,
                    message = "Payment link creation failed",
                    error = new {
                        code = errorCode,
                        details = ex.Message
                    }
                });
            }
        });
    }
}

// Data models for GP API communication
public class GpApiTokenRequest
{
    [JsonPropertyName("app_id")]
    public string AppId { get; set; } = "";

    [JsonPropertyName("nonce")]
    public string Nonce { get; set; } = "";

    [JsonPropertyName("grant_type")]
    public string GrantType { get; set; } = "";

    [JsonPropertyName("secret")]
    public string Secret { get; set; } = "";
}

public class GpApiTokenResponse
{
    [JsonPropertyName("token")]
    public string Token { get; set; } = "";

    [JsonPropertyName("type")]
    public string Type { get; set; } = "";

    [JsonPropertyName("app_id")]
    public string AppId { get; set; } = "";

    [JsonPropertyName("app_name")]
    public string AppName { get; set; } = "";

    [JsonPropertyName("time_created")]
    public string TimeCreated { get; set; } = "";

    [JsonPropertyName("seconds_to_expire")]
    public int SecondsToExpire { get; set; }

    [JsonPropertyName("email")]
    public string Email { get; set; } = "";

    [JsonPropertyName("merchant_id")]
    public string MerchantId { get; set; } = "";

    [JsonPropertyName("merchant_name")]
    public string MerchantName { get; set; } = "";

    [JsonPropertyName("transaction_processing_account_name")]
    public string TransactionProcessingAccountName { get; set; } = "";
}

public class PaymentLinkData
{
    [JsonPropertyName("account_name")]
    public string AccountName { get; set; } = "";

    [JsonPropertyName("type")]
    public string Type { get; set; } = "";

    [JsonPropertyName("usage_mode")]
    public string UsageMode { get; set; } = "";

    [JsonPropertyName("usage_limit")]
    public int UsageLimit { get; set; }

    [JsonPropertyName("reference")]
    public string Reference { get; set; } = "";

    [JsonPropertyName("name")]
    public string Name { get; set; } = "";

    [JsonPropertyName("description")]
    public string Description { get; set; } = "";

    [JsonPropertyName("shippable")]
    public string Shippable { get; set; } = "";

    [JsonPropertyName("shipping_amount")]
    public int ShippingAmount { get; set; }

    [JsonPropertyName("expiration_date")]
    public string ExpirationDate { get; set; } = "";

    [JsonPropertyName("transactions")]
    public PaymentLinkTransactions Transactions { get; set; } = new();

    [JsonPropertyName("notifications")]
    public PaymentLinkNotifications Notifications { get; set; } = new();

    [JsonPropertyName("merchant_id")]
    public string? MerchantId { get; set; }
}

public class PaymentLinkTransactions
{
    [JsonPropertyName("allowed_payment_methods")]
    public string[] AllowedPaymentMethods { get; set; } = [];

    [JsonPropertyName("channel")]
    public string Channel { get; set; } = "";

    [JsonPropertyName("country")]
    public string Country { get; set; } = "";

    [JsonPropertyName("amount")]
    public int Amount { get; set; }

    [JsonPropertyName("currency")]
    public string Currency { get; set; } = "";
}

public class PaymentLinkNotifications
{
    [JsonPropertyName("return_url")]
    public string ReturnUrl { get; set; } = "";

    [JsonPropertyName("status_url")]
    public string StatusUrl { get; set; } = "";

    [JsonPropertyName("cancel_url")]
    public string CancelUrl { get; set; } = "";
}

public class GpApiLinkResponse
{
    [JsonPropertyName("id")]
    public string Id { get; set; } = "";

    [JsonPropertyName("url")]
    public string Url { get; set; } = "";
}