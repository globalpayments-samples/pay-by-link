using dotenv.net;
using System.Text.RegularExpressions;
using GlobalPayments.Api;
using GlobalPayments.Api.Entities;
using GlobalPayments.Api.Entities.Enums;
using GlobalPayments.Api.Services;
using GlobalPayments.Api.ServiceConfigs;
using GlobalPayments.Api.Entities.GpApi;
using GlobalPayments.Api.Utils;

namespace PayByLinkSample;

/// <summary>
/// Pay by Link Processing Application
///
/// This application demonstrates payment link creation using the Global Payments SDK.
/// It provides endpoints for payment link creation, handling
/// form data to create secure payment links that can be shared with customers.
/// </summary>
public class Program
{
    public static void Main(string[] args)
    {
        // Load environment variables from .env file
        DotEnv.Load();

        // Configure the Global Payments SDK
        ConfigureSdk();

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
    /// Configure the Global Payments SDK
    ///
    /// Sets up the Global Payments SDK with necessary credentials and settings
    /// loaded from environment variables.
    /// </summary>
    private static void ConfigureSdk()
    {
        var appId = System.Environment.GetEnvironmentVariable("GP_API_APP_ID");
        var appKey = System.Environment.GetEnvironmentVariable("GP_API_APP_KEY");

        if (string.IsNullOrEmpty(appId) || string.IsNullOrEmpty(appKey))
        {
            throw new InvalidOperationException("Missing GP_API_APP_ID or GP_API_APP_KEY environment variables");
        }

        var config = new GpApiConfig
        {
            AppId = appId,
            AppKey = appKey,
            Environment = GlobalPayments.Api.Entities.Environment.TEST, // Use Environment.PRODUCTION for live transactions
            Channel = Channel.CardNotPresent,
            Country = "GB"
        };

        // Set up access token info for Pay by Link
        config.AccessTokenInfo = new AccessTokenInfo
        {
            TransactionProcessingAccountName = "paylink"
        };

        ServicesContainer.ConfigureService(config);
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

                // Create PayByLinkData object following the test pattern
                var payByLink = new PayByLinkData
                {
                    Type = PayByLinkType.PAYMENT,
                    UsageMode = PaymentMethodUsageMode.Single,
                    AllowedPaymentMethods = new[] { PaymentMethodName.Card },
                    UsageLimit = 1,
                    Name = name,
                    IsShippable = true,
                    ShippingAmount = 0m,
                    ExpirationDate = DateTime.UtcNow.AddDays(10),
                    Images = Array.Empty<string>(),
                    ReturnUrl = "https://www.example.com/returnUrl",
                    StatusUpdateUrl = "https://www.example.com/statusUrl",
                    CancelUrl = "https://www.example.com/returnUrl"
                };

                // Create the payment link using PayByLinkService (amount in dollars)
                var response = PayByLinkService.Create(payByLink, amount / 100m)
                    .WithCurrency(currency)
                    .WithClientTransactionId(GenerationUtils.GenerateRecurringKey())
                    .WithDescription(description)
                    .Execute();

                // Extract payment link URL from response
                var paymentLink = response.PayByLinkResponse.Url;

                // Return success response with payment link details
                return Results.Ok(new
                {
                    success = true,
                    message = $"Payment link created successfully! Link ID: {response.PayByLinkResponse.Id}",
                    data = new {
                        paymentLink = paymentLink,
                        linkId = response.PayByLinkResponse.Id,
                        reference = reference,
                        amount = amount,
                        currency = currency
                    }
                });
            }
            catch (Exception ex)
            {
                // Handle payment link creation errors
                return Results.BadRequest(new {
                    success = false,
                    message = "Payment link creation failed",
                    error = new {
                        code = "API_ERROR",
                        details = ex.Message
                    }
                });
            }
        });
    }
}