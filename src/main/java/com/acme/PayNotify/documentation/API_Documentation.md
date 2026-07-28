<!--
File: API_Documentation.md
Created: 2026-07-10
Author: Akshay Athavale
Use: Documents PayNotify API, design, or change details for developers and AI agents.
-->

# PayNotify API And WebSocket Documentation

This document is the source of truth for Web cashier, Mobile app, admin/setup, and WebSocket integration.

## Base URL

```text
http://{server-host}:8080
```

| Setting | Value |
|---------|-------|
| Content-Type | `application/json` |
| Authentication | JWT Bearer token |
| CORS | Enabled for all origins |
| WebSocket endpoint | `http://{server-host}:8080/ws` using SockJS + STOMP |

## Security

REST APIs and WebSocket handshakes use JWT Bearer authentication. See [SECURITY_JWT.md](SECURITY_JWT.md) for token issuance, protected routes, WebSocket token usage, configuration, and failure responses.

## Common API Response

All REST APIs return the same wrapper:

```json
{
  "success": true,
  "message": "Human readable message",
  "data": {}
}
```

| HTTP Status | Meaning |
|-------------|---------|
| `200` | Success |
| `400` | Validation or business rule error |
| `404` | Resource not found |
| `500` | Server error |

## Client Ownership

| Client | Calls |
|--------|-------|
| Web cashier app | Enterprise validation, terminal list, QR generation, status polling, PhonePe confirm/reject, manual confirm, cancel online payment |
| Mobile app | Device register/login, payment notification forwarding, terminal WebSocket subscription |
| Admin/setup UI | Department list and enterprise creation |

## Payment Status Values

| Status | Meaning | UI Action |
|--------|---------|-----------|
| `WAITING` | QR generated and waiting for customer payment | Show QR and wait |
| `PENDING` | Active payment state supported by backend matching | Treat as active/waiting |
| `PHONEPE_MATCHED_WAITING_CONFIRMATION` | PhonePe notification matched and needs cashier action | Show Confirm / Reject panel |
| `PAID_AUTO_VERIFIED` | Google Pay payment auto-verified from notification | Show success |
| `PAID_CONFIRMED_BY_CASHIER` | Cashier confirmed PhonePe or manual fallback payment | Show success |
| `EXPIRED` | QR/payment request expired | Show expired message |
| `CANCELLED_BY_CASHIER` | Cashier cancelled online payment so customer can pay cash | Stop online payment flow |
| `REJECTED_BY_CASHIER` | Cashier rejected a PhonePe notification match | Return payment to waiting/new action |

## Event Types

| Event Type | Status | Sent To | Client Action |
|------------|--------|---------|---------------|
| `PAYMENT_SUCCESS` | `PAID_AUTO_VERIFIED`, `PAID_CONFIRMED_BY_CASHIER` | Web cashier, terminal, enterprise topic | Show success |
| `PHONEPE_PAYMENT_CONFIRMATION_REQUIRED` | `PHONEPE_MATCHED_WAITING_CONFIRMATION` | Web cashier payment topic and terminal topic | Show PhonePe Confirm / Reject panel |
| `PAYMENT_STATUS_UPDATED` | `WAITING`, `EXPIRED`, `REJECTED_BY_CASHIER`, `CANCELLED_BY_CASHIER`, other non-success statuses | Web cashier, terminal, enterprise topic when applicable | Update screen based on status |

# 1. Web Cashier APIs

## 1.1 Validate Enterprise

Web cashier app calls this before loading terminals or generating QR.

```text
POST /api/enterprise/validate
```

Request:

```json
{
  "enterpriseCode": "PADM001"
}
```

Success `data`:

```json
{
  "valid": true,
  "enterpriseCode": "PADM001",
  "enterpriseName": "PADM Enterprise",
  "department": "PADM",
  "departmentCode": 1,
  "status": "VALID",
  "message": "Enterprise validated successfully",
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenExpiresAt": 1785225600000,
  "tokenType": "Bearer"
}
```

## 1.2 Get Active Terminals

Web cashier app calls this to populate terminal selection.

```text
GET /api/device/terminals?enterpriseCode=PADM001
```

Required header:

```text
Authorization: Bearer {webCashierToken}
```

Success `data`:

```json
[
  {
    "deviceId": 1,
    "enterpriseCode": "PADM001",
    "enterpriseName": "PADM Enterprise",
    "terminalId": "TERM-1782360000000",
    "role": "CASHIER",
    "deviceIdentifier": "android-device-unique-id",
    "deviceName": "Counter 1"
  }
]
```

Use `terminalId` when generating QR.

## 1.3 Generate QR

Web cashier app calls this for both Google Pay and PhonePe.

```text
POST /api/payment/qr/generate
```

Required header:

```text
Authorization: Bearer {webCashierToken}
```

Request fields:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `enterpriseCode` | string | Yes | Enterprise code |
| `terminalId` | string | Yes | Selected terminal |
| `merchantName` | string | Yes | Merchant/shop name |
| `upiId` | string | Yes | Merchant UPI ID |
| `amount` | number | Yes | Must be greater than zero |
| `sourceApp` | string | No | `GOOGLE_PAY`, `PHONEPE`, or omitted/unknown |
| `documentOwnCode` | number | No | POS bill/document reference |

Request:

```json
{
  "enterpriseCode": "PADM001",
  "terminalId": "TERM-1782360000000",
  "merchantName": "Shop Name",
  "upiId": "merchant@upi",
  "amount": 500.00,
  "sourceApp": "GOOGLE_PAY",
  "documentOwnCode": 12345
}
```

Success `data`:

```json
{
  "paymentId": "PAY-1720000000000",
  "transactionRef": "PADM-TXN-123456",
  "terminalId": "TERM-1782360000000",
  "upiUrl": "upi://pay?pa=merchant@upi&pn=Shop+Name&am=500.00&tr=PADM-TXN-123456",
  "qrImageBase64": "data:image/png;base64,iVBORw0KGgo...",
  "status": "WAITING",
  "sourceApp": "GOOGLE_PAY",
  "documentOwnCode": 12345
}
```

Common `400` errors:

- `Amount must be greater than zero`
- `UPI ID is required`
- `Merchant name is required`
- `Selected terminal already has an active payment request.`

## 1.4 Get Payment Status

Web cashier app uses this as a polling fallback when WebSocket is unavailable.

```text
GET /api/payment/status/{paymentId}
```

Required header:

```text
Authorization: Bearer {webCashierToken}
```

Success `data`:

```json
{
  "paymentId": "PAY-1720000000000",
  "enterpriseCode": "PADM001",
  "terminalId": "TERM-1782360000000",
  "transactionRef": "PADM-TXN-123456",
  "amount": 500.00,
  "status": "WAITING",
  "payerName": null,
  "utr": null,
  "notificationId": null,
  "message": null,
  "createdAt": "2026-07-08T10:30:00.000+00:00",
  "updatedAt": "2026-07-08T10:30:00.000+00:00"
}
```

`404` response:

```json
{
  "success": false,
  "message": "Payment not found",
  "data": null
}
```

## 1.5 Get Latest Pending Payment

Web cashier app uses this after refresh/reopen to resume an active payment for a terminal.

```text
GET /api/payment/latest-pending?enterpriseCode=PADM001&terminalId=TERM-1782360000000
```

Required header:

```text
Authorization: Bearer {webCashierToken}
```

Success `data` is the same shape as `GET /api/payment/status/{paymentId}`.

`404` response:

```json
{
  "success": false,
  "message": "No pending payment found",
  "data": null
}
```

## 1.6 Cancel Online Payment

Web cashier app calls this when the QR was generated but the customer cannot complete online payment and will pay by cash.

```text
POST /api/payments/{paymentId}/cancel
```

Required header:

```text
Authorization: Bearer {webCashierToken}
```

Allowed current statuses:

- `WAITING`
- `PENDING`
- `PHONEPE_MATCHED_WAITING_CONFIRMATION`

Request:

```json
{
  "reason": "Customer will pay by cash"
}
```

`reason` is optional.

Success `data`:

```json
{
  "matched": false,
  "status": "CANCELLED_BY_CASHIER",
  "paymentId": "PAY-1720000000000",
  "transactionRef": "PADM-TXN-123456",
  "expectedAmount": 500.00,
  "receivedAmount": null,
  "amountMatched": false,
  "utr": null,
  "payerName": null,
  "notificationId": null,
  "message": "Online payment cancelled by cashier. Collect cash payment."
}
```

Common `400` error:

- `Payment is not active and cannot be cancelled`

WebSocket after success:

- `/topic/payment/{paymentId}`
- `/topic/terminal/{terminalId}`
- `/topic/enterprise/{enterpriseCode}/payments`

Event status is `CANCELLED_BY_CASHIER`; event type is `PAYMENT_STATUS_UPDATED`.

## 1.7 Manual Confirm Payment

Web cashier app calls this only after the configured fallback window when the Android notification did not reach the backend and the cashier manually verified payment.

```text
POST /api/payments/{paymentId}/manual-confirm
```

Required header:

```text
Authorization: Bearer {webCashierToken}
```

Works for Google Pay and PhonePe. Do not use this when status is `PHONEPE_MATCHED_WAITING_CONFIRMATION`; use PhonePe confirm instead.

Request:

```json
{
  "utr": "MANUAL-UTR-1",
  "payerName": "Manual Payer",
  "reason": "Owner confirmed by phone"
}
```

All fields are optional.

Success `data`:

```json
{
  "matched": true,
  "status": "PAID_CONFIRMED_BY_CASHIER",
  "paymentId": "PAY-1720000000000",
  "transactionRef": "PADM-TXN-123456",
  "expectedAmount": 500.00,
  "receivedAmount": null,
  "amountMatched": true,
  "utr": "MANUAL-UTR-1",
  "payerName": "Manual Payer",
  "notificationId": null,
  "message": "Payment manually confirmed by cashier."
}
```

Common `400` errors:

- `Manual confirmation is allowed only after 3 minutes from QR generation`
- `Payment has a PhonePe notification. Use notification confirm API.`
- `Payment request is expired`
- `Payment is not waiting for manual confirmation`

Compatibility alias:

```text
POST /api/payments/{paymentId}/phonepe/manual-confirm
```

## 1.8 Confirm PhonePe Payment

Web cashier app calls this after receiving `PHONEPE_PAYMENT_CONFIRMATION_REQUIRED`.

```text
POST /api/payments/{paymentId}/phonepe/confirm
```

Required header:

```text
Authorization: Bearer {webCashierToken}
```

Request:

```json
{
  "notificationId": 501
}
```

Success `data`:

```json
{
  "matched": true,
  "status": "PAID_CONFIRMED_BY_CASHIER",
  "paymentId": "PAY-1720000000000",
  "transactionRef": "PADM-TXN-123456",
  "expectedAmount": 500.00,
  "receivedAmount": "500.00",
  "amountMatched": true,
  "utr": null,
  "payerName": "Rahul",
  "notificationId": 501,
  "message": "PhonePe payment confirmed successfully."
}
```

Common `400` errors:

- `Notification ID is required`
- `Payment is not waiting for PhonePe confirmation`
- `Notification does not match this payment request`
- `Notification is already used`
- `Notification amount does not match payment amount`

## 1.9 Reject PhonePe Payment

Web cashier app calls this when the PhonePe notification does not belong to this cashier/customer.

```text
POST /api/payments/{paymentId}/phonepe/reject
```

Required header:

```text
Authorization: Bearer {webCashierToken}
```

Request:

```json
{
  "notificationId": 501,
  "reason": "Not my customer"
}
```

`reason` is optional.

Success `data`:

```json
{
  "matched": false,
  "status": "REJECTED_BY_CASHIER",
  "paymentId": "PAY-1720000000000",
  "transactionRef": "PADM-TXN-123456",
  "expectedAmount": 500.00,
  "receivedAmount": "500.00",
  "amountMatched": false,
  "utr": null,
  "payerName": null,
  "notificationId": 501,
  "message": "PhonePe payment rejected for this payment request."
}
```

After rejection the payment request may return to `WAITING` internally when it is still usable; use WebSocket/status polling for the next UI state.

# 2. Mobile App APIs

## 2.1 Register Device

Mobile app calls this to register an Android terminal or notification listener.

```text
POST /api/device/register
```

Request:

```json
{
  "enterpriseCode": "PADM001",
  "role": "CASHIER",
  "deviceIdentifier": "android-device-unique-id",
  "deviceName": "Counter 1",
  "password": "secret"
}
```

Success `data`:

```json
{
  "deviceId": 1,
  "enterpriseCode": "PADM001",
  "enterpriseName": "PADM Enterprise",
  "role": "CASHIER",
  "terminalId": "TERM-1782360000000",
  "deviceIdentifier": "android-device-unique-id",
  "deviceName": "Counter 1",
  "status": "REGISTERED",
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenExpiresAt": 1785225600000,
  "tokenType": "Bearer"
}
```

## 2.2 Login Device

Mobile app calls this for an already registered Android device.

```text
POST /api/device/login
```

Request:

```json
{
  "deviceName": "Counter 1",
  "password": "secret"
}
```

Success `data` uses the same shape as device registration.

## 2.3 Forward Payment Notification

Mobile app notification listener calls this after receiving a UPI app notification.

```text
POST /api/payment/notify
```

Required header:

```text
Authorization: Bearer {mobileToken}
```

Request:

```json
{
  "enterpriseCode": "PADM001",
  "deviceIdentifier": "android-device-unique-id",
  "terminalId": "TERM-1782360000000",
  "appName": "Google Pay",
  "packageName": "com.google.android.apps.nbu.paisa.user",
  "title": "Google Pay",
  "message": "You received Rs. 500.00 from Rahul",
  "rawTitle": "Google Pay",
  "rawMessage": "You received Rs. 500.00 from Rahul UPI transaction ID UTR123456789",
  "amount": 500.00,
  "payerName": "Rahul",
  "extractedTxnId": "PADM-TXN-123456",
  "notificationReceivedAt": "2026-07-28T10:30:00.000+00:00",
  "transactionRef": "PADM-TXN-123456"
}
```

Notes:

- For Google Pay, `extractedTxnId` or `transactionRef` should carry the generated `PADM-TXN-*` reference when available.
- For PhonePe, transaction reference is usually not available; backend matches by enterprise, amount, terminal/time window, and cashier confirmation.
- `rawTitle` and `rawMessage` are preferred because backend parsing can use the original notification text.

Success `data` examples:

Google Pay auto verified:

```json
{
  "matched": true,
  "status": "PAID_AUTO_VERIFIED",
  "paymentId": "PAY-1720000000000",
  "transactionRef": "PADM-TXN-123456",
  "expectedAmount": 500.00,
  "receivedAmount": "500.00",
  "amountMatched": true,
  "utr": "UTR123456789",
  "payerName": "Rahul",
  "notificationId": null,
  "message": "Payment matched successfully"
}
```

PhonePe waiting for cashier confirmation:

```json
{
  "matched": true,
  "status": "PHONEPE_MATCHED_WAITING_CONFIRMATION",
  "paymentId": "PAY-1720000000000",
  "transactionRef": null,
  "expectedAmount": 500.00,
  "receivedAmount": "500.00",
  "amountMatched": true,
  "utr": null,
  "payerName": "Rahul",
  "notificationId": 501,
  "message": "PhonePe payment received. Please confirm after checking customer."
}
```

Common statuses returned to Mobile app:

- `PAID_AUTO_VERIFIED`
- `PHONEPE_MATCHED_WAITING_CONFIRMATION`
- `PHONEPE_QUEUED`
- `UNMATCHED_NOTIFICATION`
- `TRANSACTION_REF_NOT_FOUND`
- `PENDING_PAYMENT_NOT_FOUND`
- `AMOUNT_MISMATCH`
- `PAYMENT_EXPIRED`
- `DUPLICATE`

# 3. Admin/Setup APIs

## 3.1 Get Departments

Admin/setup UI calls this before enterprise creation.

```text
GET /api/enterprise/departments
```

Success `data`:

```json
[
  {
    "department": "PADM",
    "departmentCode": 1
  },
  {
    "department": "INFINITY",
    "departmentCode": 2
  }
]
```

## 3.2 Create Enterprise

Admin/setup UI calls this to create an enterprise.

```text
POST /api/enterprise/create
```

Request:

```json
{
  "enterpriseCode": "PADM001",
  "enterpriseName": "PADM Enterprise",
  "department": "PADM",
  "departmentCode": 1,
  "liveFrom": "2026-06-25T00:00:00.000+00:00"
}
```

Success `data`:

```json
{
  "id": 1,
  "enterpriseCode": "PADM001",
  "enterpriseName": "PADM Enterprise",
  "department": "PADM",
  "departmentCode": 1,
  "isActive": true,
  "liveFrom": "2026-06-25T00:00:00.000+00:00",
  "createdAt": "2026-07-28T10:30:00.000+00:00"
}
```

# 4. WebSocket

WebSocket is STOMP over SockJS. Clients subscribe to `/topic/...`; they do not send application messages.

## 4.1 Connection

| Setting | Value |
|---------|-------|
| Endpoint | `http://{server-host}:8080/ws?token={jwtToken}` |
| Protocol | SockJS + STOMP |
| Broker prefix | `/topic` |
| Application prefix | `/app` currently configured, but no client send endpoints are required |

WebSocket handshake security:

- Browser/SockJS clients should pass the token as `?token={jwtToken}` or `?access_token={jwtToken}`.
- Non-browser clients may use `Authorization: Bearer {jwtToken}` during the handshake.
- SockJS `/ws/info` probing is public; actual WebSocket/SockJS transport handshakes require a valid token.

Install frontend packages:

```bash
npm install sockjs-client @stomp/stompjs
```

Example:

```javascript
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

const paymentId = 'PAY-1720000000000';
const terminalId = 'TERM-1782360000000';
const enterpriseCode = 'PADM001';
const token = 'jwt-token-from-enterprise-validate-or-device-login';

const client = new Client({
  webSocketFactory: () => new SockJS(`http://localhost:8080/ws?token=${encodeURIComponent(token)}`),
  onConnect: () => {
    client.subscribe(`/topic/payment/${paymentId}`, (message) => {
      handlePaymentEvent(JSON.parse(message.body));
    });

    client.subscribe(`/topic/terminal/${terminalId}`, (message) => {
      handleTerminalEvent(JSON.parse(message.body));
    });

    client.subscribe(`/topic/enterprise/${enterpriseCode}/payments`, (message) => {
      handleEnterprisePaymentEvent(JSON.parse(message.body));
    });
  }
});

client.activate();
```

## 4.2 Topics

| Topic | Client | When To Subscribe | Payload |
|-------|--------|-------------------|---------|
| `/topic/payment/{paymentId}` | Web cashier | Required after QR generation | `PaymentStatusEvent` |
| `/topic/terminal/{terminalId}` | Mobile terminal, optional Web cashier | Required for terminal QR display; optional for web terminal-level monitoring | `TerminalQrEvent` on QR generation, `PaymentStatusEvent` on status update |
| `/topic/enterprise/{enterpriseCode}/payments` | Web dashboard/admin monitor | Optional enterprise-wide payment status monitoring | `PaymentStatusEvent` |

## 4.3 Terminal QR Event

Published to `/topic/terminal/{terminalId}` when Web cashier generates QR.

Payload:

```json
{
  "paymentId": "PAY-1720000000000",
  "enterpriseCode": "PADM001",
  "terminalId": "TERM-1782360000000",
  "transactionRef": "PADM-TXN-123456",
  "amount": 500.00,
  "merchantName": "Shop Name",
  "upiId": "merchant@upi",
  "upiUrl": "upi://pay?...",
  "qrImageBase64": "data:image/png;base64,...",
  "status": "WAITING",
  "message": "QR generated successfully",
  "timestamp": 1720000000000,
  "sourceApp": "GOOGLE_PAY"
}
```

## 4.4 Payment Success Event

Published after Google Pay auto verification, PhonePe cashier confirmation, or manual fallback confirmation.

Topics:

- `/topic/payment/{paymentId}`
- `/topic/terminal/{terminalId}`
- `/topic/enterprise/{enterpriseCode}/payments`

Payload:

```json
{
  "paymentId": "PAY-1720000000000",
  "enterpriseCode": "PADM001",
  "terminalId": "TERM-1782360000000",
  "status": "PAID_AUTO_VERIFIED",
  "transactionRef": "PADM-TXN-123456",
  "amount": 500.00,
  "payerName": "Rahul",
  "utr": "UTR123456789",
  "message": "Payment received successfully",
  "timestamp": 1720000000000,
  "sourceApp": "GOOGLE_PAY",
  "eventType": "PAYMENT_SUCCESS",
  "notificationId": null
}
```

## 4.5 PhonePe Confirmation Required Event

Published to `/topic/payment/{paymentId}` and `/topic/terminal/{terminalId}` when PhonePe notification is matched and cashier action is required.

Payload:

```json
{
  "paymentId": "PAY-1720000000000",
  "enterpriseCode": "PADM001",
  "terminalId": "TERM-1782360000000",
  "status": "PHONEPE_MATCHED_WAITING_CONFIRMATION",
  "transactionRef": "PADM-TXN-123456",
  "amount": 500.00,
  "payerName": "Rahul",
  "utr": null,
  "message": "PhonePe payment received. Please confirm after checking customer.",
  "timestamp": 1720000000000,
  "sourceApp": "PHONEPE",
  "eventType": "PHONEPE_PAYMENT_CONFIRMATION_REQUIRED",
  "notificationId": 501
}
```

Web cashier action:

- Save `notificationId`.
- Show payer/amount.
- Confirm using `POST /api/payments/{paymentId}/phonepe/confirm`.
- Reject using `POST /api/payments/{paymentId}/phonepe/reject`.

## 4.6 Payment Status Updated Event

Published for non-success status changes such as expiry, rejection, cancellation, and waiting updates.

Topics:

- `/topic/payment/{paymentId}`
- `/topic/terminal/{terminalId}`
- `/topic/enterprise/{enterpriseCode}/payments` when enterprise code is available

Cancellation payload:

```json
{
  "paymentId": "PAY-1720000000000",
  "enterpriseCode": "PADM001",
  "terminalId": "TERM-1782360000000",
  "status": "CANCELLED_BY_CASHIER",
  "transactionRef": "PADM-TXN-123456",
  "amount": 500.00,
  "payerName": null,
  "utr": null,
  "message": "Online payment cancelled by cashier. Collect cash payment.",
  "timestamp": 1720000000000,
  "sourceApp": "GOOGLE_PAY",
  "eventType": "PAYMENT_STATUS_UPDATED",
  "notificationId": null
}
```

Expired payload:

```json
{
  "paymentId": "PAY-1720000000000",
  "enterpriseCode": "PADM001",
  "terminalId": "TERM-1782360000000",
  "status": "EXPIRED",
  "transactionRef": "PADM-TXN-123456",
  "amount": 500.00,
  "payerName": null,
  "utr": null,
  "message": "Payment request is expired.",
  "timestamp": 1720000000000,
  "sourceApp": "PHONEPE",
  "eventType": "PAYMENT_STATUS_UPDATED",
  "notificationId": null
}
```

# 5. Flow Guides

## 5.1 Google Pay Web Cashier Flow

1. Call `POST /api/enterprise/validate`.
2. Call `GET /api/device/terminals`.
3. Call `POST /api/payment/qr/generate` with `sourceApp = "GOOGLE_PAY"`.
4. Display `qrImageBase64`.
5. Subscribe to `/topic/payment/{paymentId}`.
6. Mobile app forwards Google Pay notification to `POST /api/payment/notify`.
7. Backend sends `PAYMENT_SUCCESS` with status `PAID_AUTO_VERIFIED`.
8. If customer cannot pay online, call `POST /api/payments/{paymentId}/cancel` and collect cash.
9. If payment is verified manually after fallback window, call `POST /api/payments/{paymentId}/manual-confirm`.

## 5.2 PhonePe Web Cashier Flow

1. Call `POST /api/enterprise/validate`.
2. Call `GET /api/device/terminals`.
3. Call `POST /api/payment/qr/generate` with `sourceApp = "PHONEPE"`.
4. Display `qrImageBase64`.
5. Subscribe to `/topic/payment/{paymentId}`.
6. Mobile app forwards PhonePe notification to `POST /api/payment/notify`.
7. Backend sends `PHONEPE_PAYMENT_CONFIRMATION_REQUIRED`.
8. Cashier confirms with `POST /api/payments/{paymentId}/phonepe/confirm` or rejects with `POST /api/payments/{paymentId}/phonepe/reject`.
9. If customer cannot pay online, call `POST /api/payments/{paymentId}/cancel` and collect cash.
10. If no notification arrives and payment is manually verified after fallback window, call `POST /api/payments/{paymentId}/manual-confirm`.

## 5.3 Mobile App Flow

1. Register device using `POST /api/device/register`.
2. Login using `POST /api/device/login` when needed.
3. Subscribe terminal display to `/topic/terminal/{terminalId}` if the mobile terminal must show generated QR/status.
4. Listen to Android notifications from UPI apps.
5. Forward notification details to `POST /api/payment/notify`.

# 6. Quick Integration Checklist

## Web Cashier

- [ ] Validate enterprise.
- [ ] Load terminals.
- [ ] Generate QR with correct `sourceApp`.
- [ ] Subscribe to `/topic/payment/{paymentId}`.
- [ ] Optionally subscribe to `/topic/terminal/{terminalId}`.
- [ ] Handle `PAYMENT_SUCCESS`.
- [ ] Handle `PHONEPE_PAYMENT_CONFIRMATION_REQUIRED`.
- [ ] Handle `PAYMENT_STATUS_UPDATED`, including `CANCELLED_BY_CASHIER` and `EXPIRED`.
- [ ] Use cancel API when customer switches from online payment to cash.

## Mobile App

- [ ] Register/login device.
- [ ] Forward UPI notifications to `/api/payment/notify`.
- [ ] Include raw notification title/message whenever possible.
- [ ] Subscribe to `/topic/terminal/{terminalId}` when terminal QR/status display is required.

## Do Not Call From Web Cashier

- `POST /api/payment/notify` - Mobile app only.
- `POST /api/device/register` - Mobile app only.
- `POST /api/device/login` - Mobile app only.
