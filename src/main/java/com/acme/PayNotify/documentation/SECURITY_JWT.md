<!--
File: SECURITY_JWT.md
Created: 2026-07-28
Author: Akshay Athavale
Use: Documents JWT security for PayNotify REST APIs and WebSocket connections.
-->

# PayNotify JWT Security Documentation

PayNotify uses HMAC-SHA256 JWT tokens to protect REST APIs and WebSocket handshakes.

## Frontend Developer Setup Summary

Frontend does not create JWT tokens and does not need the JWT secret key. The backend creates the token after enterprise validation or mobile device login/register.

Frontend must configure these items:

| Item | Web Cashier Client | Mobile Client |
|------|--------------------|---------------|
| Token source | `POST /api/enterprise/validate` response `data.token` | `POST /api/device/register` or `POST /api/device/login` response `data.token` |
| REST header | `Authorization: Bearer {token}` | `Authorization: Bearer {token}` |
| WebSocket URL | `/ws?token={token}` | `/ws?token={token}` |
| Refresh action | Call enterprise validate again before expiry | Call device login again before expiry |
| Secret/key required in frontend | No | No |

Frontend should store:

```text
token
tokenType
tokenExpiresAt
enterpriseCode
terminalId, when the screen is terminal-specific
paymentId, after QR generation
```

Do not hardcode the backend JWT secret in any frontend, APK, browser bundle, or environment file used by the client. `PAYNOTIFY_JWT_SECRET` is backend-only.

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

The API response wraps this object under `data`.

## Token Issuing APIs

| Client | API | Use |
|--------|-----|-----|
| Web cashier app | `POST /api/enterprise/validate` | Validates enterprise and issues Web cashier token |
| Mobile app | `POST /api/device/register` | Registers device and issues Mobile token |
| Mobile app | `POST /api/device/login` | Logs in device and issues Mobile token |

Example Web cashier token response:

```json
{
  "success": true,
  "message": "Enterprise validation completed",
  "data": {
    "enterpriseCode": "PADM001",
    "enterpriseName": "Padm Enterprise",
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenExpiresAt": 1785225600000,
    "tokenType": "Bearer"
  }
}
```

Example Mobile token response:

```json
{
  "success": true,
  "message": "Device registered successfully",
  "data": {
    "enterpriseCode": "PADM001",
    "deviceIdentifier": "DEVICE-1",
    "terminalId": "TERM-1782360000000",
    "role": "CASHIER",
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenExpiresAt": 1785225600000,
    "tokenType": "Bearer"
  }
}
```

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

Browser clients should prefer the query string token because SockJS browser handshakes cannot reliably send custom `Authorization` headers.

JavaScript STOMP example:

```javascript
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

const apiBaseUrl = 'http://localhost:8080';
const token = sessionStorage.getItem('paynotifyToken');
const terminalId = sessionStorage.getItem('terminalId');
const paymentId = sessionStorage.getItem('paymentId');

const client = new Client({
  webSocketFactory: () => new SockJS(`${apiBaseUrl}/ws?token=${encodeURIComponent(token)}`),
  reconnectDelay: 5000,
  onConnect: () => {
    if (paymentId) {
      client.subscribe(`/topic/payment/${paymentId}`, (message) => {
        const event = JSON.parse(message.body);
        handlePaymentEvent(event);
      });
    }

    if (terminalId) {
      client.subscribe(`/topic/terminal/${terminalId}`, (message) => {
        const event = JSON.parse(message.body);
        handleTerminalEvent(event);
      });
    }
  }
});

client.activate();
```

PhonePe confirmation event can arrive on both `/topic/payment/{paymentId}` and `/topic/terminal/{terminalId}`:

```json
{
  "eventType": "PHONEPE_PAYMENT_CONFIRMATION_REQUIRED",
  "status": "PHONEPE_MATCHED_WAITING_CONFIRMATION",
  "paymentId": "PAY-1720000000000",
  "terminalId": "TERM-1782360000000",
  "notificationId": 501,
  "amount": 500.00,
  "payerName": "Rahul",
  "message": "PhonePe payment received. Please confirm after checking customer."
}
```

When this event is received, frontend should show Confirm and Reject actions.

Confirm:

```http
POST /api/payments/{paymentId}/phonepe/confirm
Authorization: Bearer {token}
Content-Type: application/json

{
  "notificationId": 501
}
```

Reject:

```http
POST /api/payments/{paymentId}/phonepe/reject
Authorization: Bearer {token}
Content-Type: application/json

{
  "notificationId": 501,
  "reason": "Not this customer's payment"
}
```

For same-enterprise, same-amount PhonePe payments, more than one terminal can receive the same `PHONEPE_PAYMENT_CONFIRMATION_REQUIRED` event. If one cashier confirms, that payment closes as paid and the other candidate screens are released back to waiting/expired by backend status updates. If one cashier rejects, only that cashier payment is released and the remaining matched cashier can still confirm the notification.

## Web Cashier Flow

1. Call `POST /api/enterprise/validate`.
2. Read `data.token`, `data.tokenType`, and `data.tokenExpiresAt`.
3. Send `Authorization: Bearer {token}` on protected REST APIs.
4. Connect WebSocket with `/ws?token={token}`.
5. Subscribe to `/topic/payment/{paymentId}` after QR generation.
6. Subscribe to `/topic/terminal/{terminalId}` when the screen needs terminal-level QR/status/PhonePe events.
7. Refresh the token by validating enterprise again before expiry.

## Mobile App Flow

1. Call `POST /api/device/register` or `POST /api/device/login`.
2. Read `data.token`, `data.tokenType`, and `data.tokenExpiresAt`.
3. Send `Authorization: Bearer {token}` when forwarding notifications to `POST /api/payment/notify`.
4. Connect terminal WebSocket with `/ws?token={token}` if the Mobile terminal needs QR/status events.
5. Refresh the token by logging in again before expiry.

## REST Client Example

Use one shared API client/interceptor for protected calls.

```javascript
const apiBaseUrl = 'http://localhost:8080';

async function apiFetch(path, options = {}) {
  const token = sessionStorage.getItem('paynotifyToken');
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {})
  };

  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...options,
    headers
  });

  if (response.status === 401) {
    sessionStorage.removeItem('paynotifyToken');
    throw new Error('Session expired. Please login again.');
  }

  return response.json();
}
```

Token expiry check before protected calls:

```javascript
function isTokenExpiredSoon(tokenExpiresAt) {
  const oneMinute = 60 * 1000;
  return !tokenExpiresAt || Number(tokenExpiresAt) <= Date.now() + oneMinute;
}
```

If expired or near expiry, refresh first:

- Web cashier: call `POST /api/enterprise/validate` again.
- Mobile app: call `POST /api/device/login` again.

## Frontend Failure Handling

| Backend Response | Frontend Action |
|------------------|-----------------|
| `401 Missing Authorization Bearer token` | Token was not attached. Redirect to setup/login or revalidate enterprise. |
| `401 Invalid JWT signature` | Clear stored token and login/validate again. |
| `401 JWT token is expired` | Clear stored token and refresh by validate/login. |
| WebSocket disconnect after token expiry | Refresh token, recreate SockJS/STOMP client with new `/ws?token={token}` URL, then resubscribe topics. |

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
