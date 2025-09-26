# Go Dependencies

This Go Pay by Link implementation requires the following dependencies:

## Runtime Dependencies

- **Go 1.23.4+** - Required Go version
- **github.com/joho/godotenv v1.5.1** - Environment variable loading from .env files

## Installation

```bash
go mod download
```

## Environment Variables

Create a `.env` file with your GP API credentials:

```env
GP_API_APP_ID=your_app_id_here
GP_API_APP_KEY=your_app_key_here
```