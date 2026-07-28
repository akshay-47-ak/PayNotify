<!--
File: SECURITY_JWT.md
Created: 2026-07-28
Author: Akshay Athavale
Use: Documents JWT security for PayNotify REST APIs and WebSocket connections.
-->

# PayNotify JWT Security Documentation

PayNotify uses HMAC-SHA256 JWT tokens to protect REST APIs and WebSocket handshakes.

## Token Format

Protected REST APIs require:

```text
Authorization: Bearer {jwtToken}
```

The token payload contains:

| Claim | Meaning |
|-------|---------|
| `sub` | Token subject, such as enterprise code or device name |
| `enterpriseCode` | Enterprise code bound to the token |
| `role` | `WEB_CASHIER`, `OWNER`, or `CASHIER` |
| `clientType` | `WEB` or `MOBILE` |
| `exp` | Expiry timestamp in epoch milliseconds |

Response fields returned by token-issuing APIs:

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenExpiresAt": 1785225600000,
  "tokenType": "Bearer"
}
```

## Token Issuing APIs

| Client | API | Use |
|--------|-----|-----|
| Web cashier app | `POST /api/enterprise/validate` | Validates enterprise and issues Web cashier token |
| Mobile app | `POST /api/device/register` | Registers device and issues Mobile token |
| Mobile app | `POST /api/device/login` | Logs in device and issues Mobile token |

## Public REST APIs

These APIs do not require a JWT token:

- `POST /api/enterprise/validate`
- `GET /api/enterprise/departments`
- `POST /api/enterprise/create`
- `POST /api/device/register`
- `POST /api/device/login`

## Protected REST APIs

These APIs require `Authorization: Bearer {jwtToken}`:

- `POST /api/payment/qr/generate`
- `GET /api/payment/status/{paymentId}`
- `GET /api/payment/latest-pending`
- `POST /api/payment/notify`
- `POST /api/payments/{paymentId}/cancel`
- `POST /api/payments/{paymentId}/manual-confirm`
- `POST /api/payments/{paymentId}/phonepe/manual-confirm`
- `POST /api/payments/{paymentId}/phonepe/confirm`
- `POST /api/payments/{paymentId}/phonepe/reject`

## Unauthorized Response

Missing token:

```json
{
  "success": false,
  "message": "Missing Authorization Bearer token",
  "data": null
}
```

Invalid token examples:

```json
{
  "success": false,
  "message": "Invalid JWT signature",
  "data": null
}
```

```json
{
  "success": false,
  "message": "JWT token is expired",
  "data": null
}
```

## WebSocket Security

WebSocket is STOMP over SockJS at:

```text
http://{server-host}:8080/ws
```

Browser/SockJS clients should pass the token in the query string:

```text
http://{server-host}:8080/ws?token={jwtToken}
```

`access_token` is also accepted:

```text
http://{server-host}:8080/ws?access_token={jwtToken}
```

Non-browser clients may pass:

```text
Authorization: Bearer {jwtToken}
```

SockJS `/ws/info` probing is public. Actual WebSocket/SockJS transport handshakes require a valid token.

## Web Cashier Flow

1. Call `POST /api/enterprise/validate`.
2. Read `data.token`, `data.tokenType`, and `data.tokenExpiresAt`.
3. Send `Authorization: Bearer {token}` on protected REST APIs.
4. Connect WebSocket with `/ws?token={token}`.
5. Refresh the token by validating enterprise again before expiry.

## Mobile App Flow

1. Call `POST /api/device/register` or `POST /api/device/login`.
2. Read `data.token`, `data.tokenType`, and `data.tokenExpiresAt`.
3. Send `Authorization: Bearer {token}` when forwarding notifications to `POST /api/payment/notify`.
4. Connect terminal WebSocket with `/ws?token={token}` if the Mobile terminal needs QR/status events.
5. Refresh the token by logging in again before expiry.

## Configuration

Runtime properties:

```properties
payment.security.jwt.secret=${PAYNOTIFY_JWT_SECRET:PayNotifyDevelopmentJwtSecretChangeThisBeforeProduction12345}
payment.security.jwt.expiry-minutes=480
```

Production requirement:

```bash
export PAYNOTIFY_JWT_SECRET="replace-with-a-long-random-secret"
```

Use a long random secret in production. Changing the secret invalidates all existing tokens.
