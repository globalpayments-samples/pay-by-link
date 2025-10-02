// Package main implements a Pay by Link server using the Global Payments GP API.
// It provides endpoints for payment link creation with secure payment link generation.
package main

import (
	"bytes"
	"crypto/sha512"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"regexp"
	"strconv"
	"strings"
	"time"

	"github.com/joho/godotenv"
)

// Config represents the configuration response sent to the client
type Config struct {
	Environment            string   `json:"environment"`
	SupportedCurrencies    []string `json:"supportedCurrencies"`
	SupportedPaymentMethods []string `json:"supportedPaymentMethods"`
}

// Response represents a standardized API response
type Response struct {
	Success bool        `json:"success"`
	Message string      `json:"message,omitempty"`
	Data    interface{} `json:"data,omitempty"`
	Error   *ErrorInfo  `json:"error,omitempty"`
}

// ErrorInfo represents error details in the response
type ErrorInfo struct {
	Code         string `json:"code"`
	Details      string `json:"details"`
	ResponseCode int    `json:"responseCode,omitempty"`
}

// PaymentLinkRequest represents the expected payment link creation request payload
type PaymentLinkRequest struct {
	Amount      string `json:"amount" form:"amount"`
	Currency    string `json:"currency" form:"currency"`
	Reference   string `json:"reference" form:"reference"`
	Name        string `json:"name" form:"name"`
	Description string `json:"description" form:"description"`
}

// PaymentLinkData represents the data structure for creating payment links via GP API
type PaymentLinkData struct {
	AccountName  string                    `json:"account_name"`
	Type         string                    `json:"type"`
	UsageMode    string                    `json:"usage_mode"`
	UsageLimit   int                       `json:"usage_limit"`
	Reference    string                    `json:"reference"`
	Name         string                    `json:"name"`
	Description  string                    `json:"description"`
	Shippable    string                    `json:"shippable"`
	ShippingAmount int                     `json:"shipping_amount"`
	ExpirationDate string                  `json:"expiration_date"`
	Transactions PaymentLinkTransactions  `json:"transactions"`
	Notifications PaymentLinkNotifications `json:"notifications"`
	MerchantID   string                    `json:"merchant_id,omitempty"`
}

// PaymentLinkTransactions represents transaction configuration for payment links
type PaymentLinkTransactions struct {
	AllowedPaymentMethods []string `json:"allowed_payment_methods"`
	Channel              string   `json:"channel"`
	Country              string   `json:"country"`
	Amount               int      `json:"amount"`
	Currency             string   `json:"currency"`
}

// PaymentLinkNotifications represents notification URLs for payment links
type PaymentLinkNotifications struct {
	ReturnURL string `json:"return_url"`
	StatusURL string `json:"status_url"`
	CancelURL string `json:"cancel_url"`
}

// PaymentLinkResponse represents the response data for successful payment link creation
type PaymentLinkResponse struct {
	PaymentLink string `json:"paymentLink"`
	LinkID      string `json:"linkId"`
	Reference   string `json:"reference"`
	Amount      int    `json:"amount"`
	Currency    string `json:"currency"`
}

// GPApiTokenRequest represents the GP API token request
type GPApiTokenRequest struct {
	AppID     string `json:"app_id"`
	Nonce     string `json:"nonce"`
	GrantType string `json:"grant_type"`
	Secret    string `json:"secret"`
}

// GPApiTokenResponse represents the GP API token response
type GPApiTokenResponse struct {
	Token                          string `json:"token"`
	Type                           string `json:"type"`
	AppID                          string `json:"app_id"`
	AppName                        string `json:"app_name"`
	TimeCreated                    string `json:"time_created"`
	SecondsToExpire                int    `json:"seconds_to_expire"`
	Email                          string `json:"email"`
	MerchantID                     string `json:"merchant_id"`
	MerchantName                   string `json:"merchant_name"`
	TransactionProcessingAccountName string `json:"transaction_processing_account_name"`
}

// GPApiLinkResponse represents the GP API payment link creation response
type GPApiLinkResponse struct {
	ID  string `json:"id"`
	URL string `json:"url"`
}

// sanitizeReference removes invalid characters from the reference input.
// It only allows alphanumeric characters, spaces, hyphens, and hash symbols,
// limiting the length to 100 characters.
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

// generateSecret generates a secret hash using SHA512 for GP API authentication.
// The secret is created as SHA512(NONCE + APP-KEY).
func generateSecret(nonce, appKey string) string {
	data := nonce + appKey
	hash := sha512.Sum512([]byte(data))
	return strings.ToLower(hex.EncodeToString(hash[:]))
}

// generateAccessToken generates an access token for GP API using app credentials
func generateAccessToken() (*GPApiTokenResponse, error) {
	appID := os.Getenv("GP_API_APP_ID")
	appKey := os.Getenv("GP_API_APP_KEY")

	if appID == "" || appKey == "" {
		return nil, fmt.Errorf("missing GP_API_APP_ID or GP_API_APP_KEY environment variables")
	}

	// Generate nonce using the same format as .NET SDK
	nonce := time.Now().Format("01/02/2006 03:04:05.000 PM")

	tokenRequest := GPApiTokenRequest{
		AppID:     appID,
		Nonce:     nonce,
		GrantType: "client_credentials",
		Secret:    generateSecret(nonce, appKey),
	}

	requestBody, err := json.Marshal(tokenRequest)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal token request: %w", err)
	}

	client := &http.Client{Timeout: 30 * time.Second}
	req, err := http.NewRequest("POST", "https://apis.sandbox.globalpay.com/ucp/accesstoken", bytes.NewBuffer(requestBody))
	if err != nil {
		return nil, fmt.Errorf("failed to create token request: %w", err)
	}

	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-GP-Api-Key", appKey)
	req.Header.Set("X-GP-Version", "2021-03-22")
	req.Header.Set("Accept", "application/json")
	req.Header.Set("User-Agent", "PayByLink-Go/1.0")

	resp, err := client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("failed to execute token request: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read token response: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("token request failed with status %d: %s", resp.StatusCode, string(body))
	}

	var tokenResponse GPApiTokenResponse
	if err := json.Unmarshal(body, &tokenResponse); err != nil {
		return nil, fmt.Errorf("failed to unmarshal token response: %w", err)
	}

	return &tokenResponse, nil
}

// createPaymentLink makes a direct API call to GP API to create a payment link
func createPaymentLink(paymentLinkData PaymentLinkData, accessToken string) (*GPApiLinkResponse, error) {
	requestBody, err := json.Marshal(paymentLinkData)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal payment link data: %w", err)
	}

	client := &http.Client{Timeout: 30 * time.Second}
	req, err := http.NewRequest("POST", "https://apis.sandbox.globalpay.com/ucp/links", bytes.NewBuffer(requestBody))
	if err != nil {
		return nil, fmt.Errorf("failed to create payment link request: %w", err)
	}

	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+accessToken)
	req.Header.Set("X-GP-Version", "2021-03-22")

	resp, err := client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("failed to execute payment link request: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read payment link response: %w", err)
	}

	if resp.StatusCode != http.StatusCreated && resp.StatusCode != http.StatusOK {
		var errorMsg string
		// Try to parse error response for better error details
		var errorResponse map[string]interface{}
		if err := json.Unmarshal(body, &errorResponse); err == nil {
			if desc, ok := errorResponse["error_description"]; ok {
				errorMsg = fmt.Sprintf("%v", desc)
			} else if msg, ok := errorResponse["message"]; ok {
				errorMsg = fmt.Sprintf("%v", msg)
			} else {
				errorMsg = string(body)
			}
		} else {
			errorMsg = string(body)
		}
		return nil, fmt.Errorf("payment link creation failed with status %d: %s", resp.StatusCode, errorMsg)
	}

	var linkResponse GPApiLinkResponse
	if err := json.Unmarshal(body, &linkResponse); err != nil {
		return nil, fmt.Errorf("failed to unmarshal payment link response: %w", err)
	}

	return &linkResponse, nil
}

// handleConfig handles the /config endpoint
func handleConfig(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	response := Response{
		Success: true,
		Data: Config{
			Environment:             "sandbox", // Use "production" for live transactions
			SupportedCurrencies:     []string{"EUR", "USD", "GBP"},
			SupportedPaymentMethods: []string{"CARD"},
		},
	}
	json.NewEncoder(w).Encode(response)
}

// handleCreatePaymentLink handles the /create-payment-link endpoint
func handleCreatePaymentLink(w http.ResponseWriter, r *http.Request) {
	// Ensure endpoint only accepts POST requests
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	// Parse and validate the form data or JSON
	var req PaymentLinkRequest

	// Check Content-Type and parse accordingly
	contentType := r.Header.Get("Content-Type")
	if strings.Contains(contentType, "application/json") {
		// Parse JSON request
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			w.Header().Set("Content-Type", "application/json")
			errorResponse := Response{
				Success: false,
				Message: "Payment link creation failed",
				Error: &ErrorInfo{
					Code:    "INVALID_JSON",
					Details: "Error parsing JSON request body",
				},
			}
			w.WriteHeader(http.StatusBadRequest)
			json.NewEncoder(w).Encode(errorResponse)
			return
		}
	} else {
		// Parse form data
		if err := r.ParseForm(); err != nil {
			w.Header().Set("Content-Type", "application/json")
			errorResponse := Response{
				Success: false,
				Message: "Payment link creation failed",
				Error: &ErrorInfo{
					Code:    "FORM_PARSE_ERROR",
					Details: "Error parsing form data",
				},
			}
			w.WriteHeader(http.StatusBadRequest)
			json.NewEncoder(w).Encode(errorResponse)
			return
		}

		// Extract form values
		req.Amount = r.Form.Get("amount")
		req.Currency = r.Form.Get("currency")
		req.Reference = r.Form.Get("reference")
		req.Name = r.Form.Get("name")
		req.Description = r.Form.Get("description")
	}

	// Validate required fields
	requiredFields := []string{"amount", "currency", "reference", "name", "description"}
	receivedFields := []string{}

	if req.Amount != "" { receivedFields = append(receivedFields, "amount") }
	if req.Currency != "" { receivedFields = append(receivedFields, "currency") }
	if req.Reference != "" { receivedFields = append(receivedFields, "reference") }
	if req.Name != "" { receivedFields = append(receivedFields, "name") }
	if req.Description != "" { receivedFields = append(receivedFields, "description") }

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

	if len(missingFields) > 0 {
		w.Header().Set("Content-Type", "application/json")
		errorResponse := Response{
			Success: false,
			Message: "Payment link creation failed",
			Error: &ErrorInfo{
				Code:    "MISSING_REQUIRED_FIELDS",
				Details: fmt.Sprintf("Missing required fields. Received: %s", strings.Join(receivedFields, ", ")),
			},
		}
		w.WriteHeader(http.StatusBadRequest)
		json.NewEncoder(w).Encode(errorResponse)
		return
	}

	// Parse and validate amount
	amount, err := strconv.Atoi(req.Amount)
	if err != nil || amount <= 0 {
		w.Header().Set("Content-Type", "application/json")
		errorResponse := Response{
			Success: false,
			Message: "Payment link creation failed",
			Error: &ErrorInfo{
				Code:    "INVALID_AMOUNT",
				Details: "Invalid amount",
			},
		}
		w.WriteHeader(http.StatusBadRequest)
		json.NewEncoder(w).Encode(errorResponse)
		return
	}

	// Sanitize and prepare data
	reference := sanitizeReference(req.Reference)
	name := strings.TrimSpace(req.Name)
	if len(name) > 100 {
		name = name[:100]
	}
	description := strings.TrimSpace(req.Description)
	if len(description) > 500 {
		description = description[:500]
	}
	currency := strings.ToUpper(strings.TrimSpace(req.Currency))

	// Generate access token
	tokenResponse, err := generateAccessToken()
	if err != nil {
		w.Header().Set("Content-Type", "application/json")
		errorResponse := Response{
			Success: false,
			Message: "Payment link creation failed",
			Error: &ErrorInfo{
				Code:    "TOKEN_GENERATION_ERROR",
				Details: err.Error(),
			},
		}
		w.WriteHeader(http.StatusInternalServerError)
		json.NewEncoder(w).Encode(errorResponse)
		return
	}

	// Set account name from token response or default to "paylink"
	accountName := "paylink"
	if tokenResponse.TransactionProcessingAccountName != "" {
		accountName = tokenResponse.TransactionProcessingAccountName
	}

	// Create PayByLink data object
	expirationDate := time.Now().Add(10 * 24 * time.Hour).Format("2006-01-02 15:04:05")

	payByLinkData := PaymentLinkData{
		AccountName:    accountName,
		Type:          "PAYMENT",  // PayByLinkType::PAYMENT
		UsageMode:     "SINGLE",   // PaymentMethodUsageMode::SINGLE
		UsageLimit:    1,          // usageLimit = 1
		Reference:     reference,
		Name:          name,
		Description:   description,
		Shippable:     "YES",
		ShippingAmount: 0,         // shippingAmount = 0
		ExpirationDate: expirationDate, // +10 days
		Transactions: PaymentLinkTransactions{
			AllowedPaymentMethods: []string{"CARD"}, // allowedPaymentMethods = [PaymentMethodName::CARD]
			Channel:              "CNP",             // Card Not Present
			Country:              "GB",
			Amount:               amount,            // Amount in cents
			Currency:             currency,
		},
		Notifications: PaymentLinkNotifications{
			ReturnURL: "https://www.example.com/returnUrl",  // returnUrl
			StatusURL: "https://www.example.com/statusUrl",  // statusUpdateUrl
			CancelURL: "https://www.example.com/returnUrl",  // cancelUrl
		},
	}

	// Add merchant_id if available
	if tokenResponse.MerchantID != "" {
		payByLinkData.MerchantID = tokenResponse.MerchantID
	}

	// Create payment link via GP API
	linkResponse, err := createPaymentLink(payByLinkData, tokenResponse.Token)
	if err != nil {
		w.Header().Set("Content-Type", "application/json")
		errorResponse := Response{
			Success: false,
			Message: "Payment link creation failed",
			Error: &ErrorInfo{
				Code:    "API_ERROR",
				Details: err.Error(),
			},
		}
		w.WriteHeader(http.StatusBadRequest)
		json.NewEncoder(w).Encode(errorResponse)
		return
	}

	// Validate payment link URL
	if linkResponse.URL == "" {
		w.Header().Set("Content-Type", "application/json")
		errorResponse := Response{
			Success: false,
			Message: "Payment link creation failed",
			Error: &ErrorInfo{
				Code:    "INVALID_RESPONSE",
				Details: "No payment link URL in response",
			},
		}
		w.WriteHeader(http.StatusInternalServerError)
		json.NewEncoder(w).Encode(errorResponse)
		return
	}

	// Return success response
	w.Header().Set("Content-Type", "application/json")
	successResponse := Response{
		Success: true,
		Message: fmt.Sprintf("Payment link created successfully! Link ID: %s", linkResponse.ID),
		Data: PaymentLinkResponse{
			PaymentLink: linkResponse.URL,
			LinkID:      linkResponse.ID,
			Reference:   reference,
			Amount:      amount,
			Currency:    currency,
		},
	}
	json.NewEncoder(w).Encode(successResponse)
}

func main() {
	// Initialize environment
	err := godotenv.Load()
	if err != nil {
		log.Printf("Warning: Error loading .env file: %v", err)
	}

	// Validate GP API credentials
	if os.Getenv("GP_API_APP_ID") == "" || os.Getenv("GP_API_APP_KEY") == "" {
		log.Fatal("Missing required environment variables: GP_API_APP_ID and GP_API_APP_KEY")
	}

	log.Printf("GP API App ID: %s", os.Getenv("GP_API_APP_ID"))

	// Set up routes
	http.Handle("/", http.FileServer(http.Dir("static")))
	http.Handle("/config", http.HandlerFunc(handleConfig))
	http.Handle("/create-payment-link", http.HandlerFunc(handleCreatePaymentLink))

	// Get port from environment variable or use default
	port := os.Getenv("PORT")
	if port == "" {
		port = "8000"
	}

	log.Printf("Server starting on http://localhost:%s", port)
	log.Printf("Server also accessible at http://127.0.0.1:%s", port)
	log.Printf("Endpoints:")
	log.Printf("  GET  /config              - Config endpoint")
	log.Printf("  POST /create-payment-link - Create payment link endpoint")
	log.Fatal(http.ListenAndServe("0.0.0.0:"+port, nil))
}