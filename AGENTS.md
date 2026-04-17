# Global Payments Pay by Link

> Create shareable payment links via the Global Payments GP API, demonstrated in Node.js, Python, PHP, Java, .NET, and Go.

## Critical Patterns

1. **Amount units differ between SDK and direct-API implementations.** PHP, Java, and .NET use `PayByLinkService`, which expects the amount in dollars — so they divide the incoming cents value by 100 before calling `.create()`. Node.js, Python, and Go post the raw integer cents directly to the REST endpoint. Mixing these up silently creates links for 100× the intended amount.

2. **The `account_name` field must be `"paylink"` for Pay by Link to work.** SDK implementations set `AccessTokenInfo.transactionProcessingAccountName = "paylink"` in the SDK config. Direct-API implementations read `transaction_processing_account_name` from the token response and fall back to `"paylink"` if absent. Without this, the GP API will return an error or use the wrong account.

3. **`country` must be `"GB"` regardless of transaction currency.** All six implementations hardcode this. It reflects the merchant registration country, not the customer's country or the payment currency, and is easy to miss when adapting the code.

4. **Token auth and link creation use different auth schemes.** The `POST /ucp/accesstoken` call includes `X-GP-Api-Key` in the header alongside a SHA512(nonce + appKey) body secret. Subsequent calls to `/ucp/links` use `Authorization: Bearer {token}` — no `X-GP-Api-Key` header. The Node.js implementation (server.js:75–85) shows the correct header split clearly.

## Repository Structure

### Node.js (Express, direct GP API)
- [`nodejs/server.js`](nodejs/server.js) — single-file server; `generateAccessToken` (L49–93), `handleCreatePaymentLink` (L125–287)
- [`nodejs/index.html`](nodejs/index.html) — payment link creation form
- [`nodejs/package.json`](nodejs/package.json) — lists `globalpayments-api` as a dependency but the implementation uses direct REST calls instead; the SDK is not imported

### Python (Flask, direct GP API)
- [`python/server.py`](python/server.py) — `generate_access_token` (L62–102), `create_payment_link_api` (L104–142), `create_payment_link` route (L164–313)
- [`python/requirements.txt`](python/requirements.txt) — Flask 3.0, requests 2.31

### PHP (native PHP + Global Payments SDK)
- [`php/create-payment-link.php`](php/create-payment-link.php) — `configureSdk` (L49–69) sets up SDK; `PayByLinkService::create()` call at L132 (amount divided by 100)
- [`php/config.php`](php/config.php) — optional separate config endpoint
- [`php/composer.json`](php/composer.json) — `globalpayments/php-sdk` ^13.3

### Java (Jakarta EE servlet + Global Payments SDK)
- [`java/src/main/java/com/globalpayments/example/ProcessPaymentServlet.java`](java/src/main/java/com/globalpayments/example/ProcessPaymentServlet.java) — `configureSdk` (L71–85), `createPaymentLinkViaSdk` (L228–273); amount divided at L250
- [`java/pom.xml`](java/pom.xml) — `globalpayments-sdk` 14.2.20, Jakarta Servlet 5.0

### .NET (ASP.NET Core minimal API + Global Payments SDK)
- [`dotnet/Program.cs`](dotnet/Program.cs) — `ConfigureSdk` (L52–78), `ConfigurePaymentLinkEndpoint` (L123–225); amount divided at L189
- [`dotnet/dotnet.csproj`](dotnet/dotnet.csproj) — `GlobalPayments.Api` 9.0.16, net9.0

### Go (net/http, direct GP API)
- [`go/main.go`](go/main.go) — `generateAccessToken` (L150–206), `createPaymentLink` (L209–260), `handleCreatePaymentLink` (L277–501)
- [`go/go.mod`](go/go.mod) — only non-stdlib dependency is `godotenv`

### Shared
- [`index.html`](index.html) — root copy of the frontend form (each language also has its own copy)
- [`docker-compose.yml`](docker-compose.yml) — runs all six implementations together

## API Surface

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/config` | Returns supported currencies, payment methods, and environment label |
| POST | `/create-payment-link` | Creates a GP API payment link; returns URL and link ID |

All implementations expose identical endpoints with identical JSON response shapes.

## Environment Variables

```bash
GP_API_APP_ID=your_app_id      # GP API application ID
GP_API_APP_KEY=your_app_key    # GP API application key
GP_API_ENVIRONMENT=sandbox     # "sandbox" or "production" (informational; SDK uses Environment.TEST/PRODUCTION)
PORT=8000                      # Optional; defaults to 8000
```

Each language reads from a `.env` file in its own directory. No `.env.sample` files exist — copy the format above.

## API Request Shape

Applies to **Node.js, Python, and Go** (direct HTTP implementations only).

**Step 1 — Get access token:**
- `POST https://apis.sandbox.globalpay.com/ucp/accesstoken`
- Headers: `X-GP-Api-Key: {appKey}`, `X-GP-Version: 2021-03-22`, `Content-Type: application/json`
- Body: `{app_id, nonce, grant_type: "client_credentials", secret: SHA512(nonce + appKey)}`
- The `nonce` format is `MM/DD/YYYY hh:mm:ss.mmm AM|PM` — see go/main.go:159 for the canonical format string

**Step 2 — Create link:**
- `POST https://apis.sandbox.globalpay.com/ucp/links`
- Headers: `Authorization: Bearer {token}`, `X-GP-Version: 2021-03-22`
- Key body fields:
  - `account_name` — from `transaction_processing_account_name` in token response, or `"paylink"`
  - `type: "PAYMENT"`, `usage_mode: "SINGLE"`, `usage_limit: 1`
  - `transactions.channel: "CNP"`, `transactions.country: "GB"`
  - `transactions.amount` — integer cents (NOT divided by 100 here)
  - `shippable: "YES"`, `shipping_amount: 0`
  - `expiration_date` — ISO datetime string, 10 days out

## Architecture Summary

**Link creation:** HTML form → `POST /create-payment-link` → token request → `POST /ucp/links` → return `url` and `id` from GP API response

**SDK vs direct API:** PHP/Java/.NET delegate auth and request construction to the SDK (`PayByLinkService`); Node.js/Python/Go implement the two-step token + link flow manually with raw HTTP.

## Security Notes

These demos have no authentication on the `/create-payment-link` endpoint, use `example.com` placeholder notification URLs, and log credentials to stdout in some debug paths. For production: add auth middleware, replace notification URLs, use secrets management instead of `.env` files, and enable HTTPS.

## SDK Versions

- **PHP**: `globalpayments/php-sdk` ^13.3
- **Java**: `globalpayments-sdk` (com.heartlandpaymentsystems) 14.2.20
- **.NET**: `GlobalPayments.Api` 9.0.16
- **Node.js / Python / Go**: no GP SDK — direct REST calls to `https://apis.sandbox.globalpay.com/ucp`
