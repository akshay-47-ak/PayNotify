# PayNotify — Web Cashier API Documentation

> **Audience:** Web frontend team (Cashier screen)  
> **Not included:** Android device APIs (`/api/device/register`, `/api/device/login`, `/api/payment/notify`)

---

## Base URL

```
http://{server-host}:8080
```

| Setting | Value |
|---------|-------|
| Content-Type | `application/json` |
| Authentication | None (no auth headers required) |
| CORS | Enabled for all origins |

---

## Common Response Format

All APIs return the same wrapper:

```json
{
  "success": true,
  "message": "Human readable message",
  "data": { }
}
```

| HTTP Status | Meaning |
|-------------|---------|
| `200` | Success |
| `400` | Validation / business error (`success: false`) |
| `404` | Resource not found |
| `500` | Server error |

---

## Payment Status Values (UI Reference)

| Status | Meaning | UI Action |
|--------|---------|-----------|
| `WAITING` | QR generated, waiting for customer payment | Show QR |
| `PHONEPE_MATCHED_WAITING_CONFIRMATION` | PhonePe payment detected, needs cashier action | Show Confirm / Reject panel |
| `PAID_AUTO_VERIFIED` | Google Pay — payment auto-verified | Show success screen |
| `PAID_CONFIRMED_BY_CASHIER` | PhonePe — cashier confirmed payment | Show success screen |
| `EXPIRED` | QR timed out (15 min) | Show expired message |
| `REJECTED_BY_CASHIER` | Cashier rejected PhonePe payment | Return to waiting / new QR |

---

# Part 1 — Setup APIs (Before Payment)

These are called once when the cashier screen loads.

---

## 1.1 Validate Enterprise

```
POST /api/enterprise/validate
```

**Request:**
```json
{
  "enterpriseCode": "PADM001"
}
```

**Response (`data`):**
```json
{
  "valid": true,
  "enterpriseCode": "PADM001",
  "enterpriseName": "PADM Enterprise",
  "department": "PADM",
  "departmentCode": 1,
  "status": "VALID",
  "message": "Enterprise is valid"
}
```

---

## 1.2 Get Active Terminals

Used to populate the terminal dropdown on the cashier screen.

```
GET /api/device/terminals?enterpriseCode=PADM001
```

**Response (`data`):**
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

> Use `terminalId` when generating QR.

---

# Part 2 — Shared Payment APIs

Used by both Google Pay and PhonePe flows.

---

## 2.1 Generate QR Code

```
POST /api/payment/qr/generate
```

**Request:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `enterpriseCode` | string | Yes | Enterprise code |
| `terminalId` | string | Yes | Selected terminal |
| `merchantName` | string | Yes | Shop / merchant name |
| `upiId` | string | Yes | Merchant UPI ID |
| `amount` | number | Yes | Must be > 0 |
| `sourceApp` | string | Yes | `"GOOGLE_PAY"` or `"PHONEPE"` |
| `documentOwnCode` | number | No | POS bill / document reference |
| `cashierId` | number | No | Cashier user ID |
| `cashierSessionId` | string | No | Cashier session ID |
| `branchId` | number | No | Branch ID |

**Example Request:**
```json
{
  "enterpriseCode": "PADM001",
  "terminalId": "TERM-1782360000000",
  "merchantName": "Shop Name",
  "upiId": "merchant@upi",
  "amount": 500.00,
  "sourceApp": "GOOGLE_PAY",
  "documentOwnCode": 12345,
  "cashierId": 10,
  "cashierSessionId": "session-abc",
  "branchId": 1
}
```

**Success Response (`data`):**
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

**Common Errors (`400`):**
- `"Amount must be greater than zero"`
- `"UPI ID is required"`
- `"Selected terminal already has an active payment request."`

> QR expires after **15 minutes**.

---

## 2.2 Get Payment Status (Polling Fallback)

Use when WebSocket is disconnected or as a backup.

```
GET /api/payment/status/{paymentId}
```

**Example:** `GET /api/payment/status/PAY-1720000000000`

**Response (`data`):**
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

**404:** `{ "success": false, "message": "Payment not found", "data": null }`

---

## 2.3 Get Latest Pending Payment

Use when cashier refreshes the page and needs to resume an active payment.

```
GET /api/payment/latest-pending?enterpriseCode=PADM001&terminalId=TERM-1782360000000
```

Returns the same `PaymentStatusResponse` shape as **2.2**.  
**404** if no pending payment exists.

---

# Part 3 — Google Pay Flow (Web Cashier)

Google Pay payments are **auto-verified**. The cashier only generates QR and waits for success. No confirm/reject step.

---

## Flow

```
1. Cashier selects terminal + enters amount
2. POST /api/payment/qr/generate  (sourceApp = "GOOGLE_PAY")
3. Display QR from qrImageBase64
4. Connect WebSocket → subscribe to /topic/payment/{paymentId}
5. Customer pays via Google Pay on their phone
6. Android device forwards notification to backend (you don't call this)
7. Backend auto-matches payment → status = PAID_AUTO_VERIFIED
8. WebSocket sends eventType = "PAYMENT_SUCCESS"
9. Show success screen
```

---

## Generate QR — Google Pay Example

```json
{
  "enterpriseCode": "PADM001",
  "terminalId": "TERM-1782360000000",
  "merchantName": "Shop Name",
  "upiId": "merchant@upi",
  "amount": 500.00,
  "sourceApp": "GOOGLE_PAY",
  "cashierId": 10,
  "cashierSessionId": "session-abc"
}
```

---

## WebSocket Events — Google Pay

Subscribe to: `/topic/payment/{paymentId}`

### On Success

```json
{
  "eventType": "PAYMENT_SUCCESS",
  "paymentId": "PAY-1720000000000",
  "enterpriseCode": "PADM001",
  "terminalId": "TERM-1782360000000",
  "status": "PAID_AUTO_VERIFIED",
  "transactionRef": "PADM-TXN-123456",
  "amount": 500.00,
  "payerName": "Rahul",
  "utr": "UTR123456789",
  "message": "Payment received successfully",
  "sourceApp": "GOOGLE_PAY",
  "timestamp": 1720000000000,
  "notificationId": null
}
```

**UI:** When `eventType === "PAYMENT_SUCCESS"` → show success screen with amount, payer name, UTR.

### On Expired

```json
{
  "eventType": "PAYMENT_STATUS_UPDATED",
  "status": "EXPIRED",
  "message": "Payment request expired",
  "paymentId": "PAY-1720000000000",
  "timestamp": 1720000000000
}
```

---

## Polling Fallback — Google Pay

If WebSocket disconnects, poll every 3–5 seconds:

```
GET /api/payment/status/{paymentId}
```

Stop polling when `status` is `PAID_AUTO_VERIFIED` or `EXPIRED`.

---

# Part 4 — PhonePe Flow (Web Cashier)

PhonePe payments need **cashier confirmation**. The backend matches by amount + time window (PhonePe notifications don't carry transaction reference). Cashier must confirm or reject on the web screen.

---

## Flow

```
1. Cashier selects terminal + enters amount
2. POST /api/payment/qr/generate  (sourceApp = "PHONEPE")
3. Display QR from qrImageBase64
4. Connect WebSocket → subscribe to /topic/payment/{paymentId}
5. Customer pays via PhonePe
6. Android device forwards notification to backend (you don't call this)
7. Backend matches → status = PHONEPE_MATCHED_WAITING_CONFIRMATION
8. WebSocket sends eventType = "PHONEPE_PAYMENT_CONFIRMATION_REQUIRED"
9. Show Confirm / Reject panel (amount, payerName, notificationId)
10a. Cashier confirms → POST /api/payments/{paymentId}/phonepe/confirm
10b. Cashier rejects  → POST /api/payments/{paymentId}/phonepe/reject
11. On confirm → WebSocket sends PAYMENT_SUCCESS
```

---

## Generate QR — PhonePe Example

```json
{
  "enterpriseCode": "PADM001",
  "terminalId": "TERM-1782360000000",
  "merchantName": "Shop Name",
  "upiId": "merchant@upi",
  "amount": 500.00,
  "sourceApp": "PHONEPE",
  "cashierId": 10,
  "cashierSessionId": "session-abc"
}
```

---

## WebSocket Events — PhonePe

Subscribe to: `/topic/payment/{paymentId}`

> PhonePe confirmation events are sent **only** to `/topic/payment/{paymentId}` — not to enterprise-wide topics.

### Confirmation Required

```json
{
  "eventType": "PHONEPE_PAYMENT_CONFIRMATION_REQUIRED",
  "paymentId": "PAY-1720000000000",
  "enterpriseCode": "PADM001",
  "terminalId": "TERM-1782360000000",
  "status": "PHONEPE_MATCHED_WAITING_CONFIRMATION",
  "amount": 500.00,
  "payerName": "Rahul",
  "notificationId": 501,
  "message": "PhonePe payment received. Please confirm after checking customer.",
  "timestamp": 1720000000000
}
```

**UI:** Show a panel with:
- Amount: `amount`
- Payer: `payerName`
- **Confirm** and **Reject** buttons
- Save `notificationId` — required for confirm/reject API calls

### On Confirm Success

```json
{
  "eventType": "PAYMENT_SUCCESS",
  "paymentId": "PAY-1720000000000",
  "status": "PAID_CONFIRMED_BY_CASHIER",
  "amount": 500.00,
  "payerName": "Rahul",
  "message": "PhonePe payment confirmed successfully.",
  "timestamp": 1720000000000
}
```

### On Reject

```json
{
  "eventType": "PAYMENT_STATUS_UPDATED",
  "status": "WAITING",
  "message": "PhonePe payment rejected by cashier.",
  "paymentId": "PAY-1720000000000",
  "timestamp": 1720000000000
}
```

---

## 4.1 Confirm PhonePe Payment

```
POST /api/payments/{paymentId}/phonepe/confirm
```

`{paymentId}` = value from QR generate response (e.g. `PAY-1720000000000`)

**Request:**
```json
{
  "cashierId": 10,
  "notificationId": 501
}
```

| Field | Type | Required |
|-------|------|----------|
| `cashierId` | number | Yes |
| `notificationId` | number | Yes (from WebSocket event) |

**Success Response (`data`):**
```json
{
  "matched": true,
  "status": "PAID_CONFIRMED_BY_CASHIER",
  "paymentId": "PAY-1720000000000",
  "transactionRef": "PADM-TXN-123456",
  "expectedAmount": 500.00,
  "receivedAmount": "500.00",
  "amountMatched": true,
  "payerName": "Rahul",
  "notificationId": 501,
  "message": "PhonePe payment confirmed successfully."
}
```

**Common Errors (`400`):**
- `"Cashier ID and notification ID are required"`
- `"Payment is not waiting for PhonePe confirmation"`
- `"Notification is already used"`

---

## 4.2 Reject PhonePe Payment

```
POST /api/payments/{paymentId}/phonepe/reject
```

**Request:**
```json
{
  "cashierId": 10,
  "notificationId": 501,
  "reason": "Not my customer"
}
```

| Field | Type | Required |
|-------|------|----------|
| `cashierId` | number | Yes |
| `notificationId` | number | Yes |
| `reason` | string | No |

**Success Response (`data`):**
```json
{
  "matched": false,
  "status": "WAITING",
  "paymentId": "PAY-1720000000000",
  "expectedAmount": 500.00,
  "receivedAmount": "500.00",
  "amountMatched": true,
  "payerName": "Rahul",
  "notificationId": 501,
  "message": "PhonePe payment rejected by cashier."
}
```

---

## Polling Fallback — PhonePe

Poll `GET /api/payment/status/{paymentId}` when WebSocket is down.

When status becomes `PHONEPE_MATCHED_WAITING_CONFIRMATION`, show the confirm panel using `notificationId` and `payerName` from the response:

```json
{
  "paymentId": "PAY-1720000000000",
  "amount": 500.00,
  "status": "PHONEPE_MATCHED_WAITING_CONFIRMATION",
  "payerName": "Rahul",
  "notificationId": 501,
  "message": "PhonePe payment received. Please confirm after checking customer."
}
```

---

# Part 5 — WebSocket (STOMP over SockJS)

Server-push only. Web client **subscribes** — no messages need to be sent from the web app.

---

## Connection

| Setting | Value |
|---------|-------|
| Endpoint | `http://{server-host}:8080/ws` (SockJS) |
| Protocol | STOMP |
| Subscribe prefix | `/topic` |

### Libraries

```bash
npm install sockjs-client @stomp/stompjs
```

### Connect & Subscribe Example

```javascript
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

const paymentId = 'PAY-1720000000000';
const terminalId = 'TERM-1782360000000';

const client = new Client({
  webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
  onConnect: () => {
    // Primary — payment status updates
    client.subscribe(`/topic/payment/${paymentId}`, (message) => {
      const event = JSON.parse(message.body);
      handlePaymentEvent(event);
    });

    // Optional — terminal-level QR + status updates
    client.subscribe(`/topic/terminal/${terminalId}`, (message) => {
      const event = JSON.parse(message.body);
      handleTerminalEvent(event);
    });
  },
});

client.activate();
```

---

## Subscribe Topics

| Topic | When to Use | Payload |
|-------|-------------|---------|
| `/topic/payment/{paymentId}` | **Required** — all payment status updates + PhonePe confirm prompt | `PaymentStatusEvent` |
| `/topic/terminal/{terminalId}` | Optional — QR generation event + status updates | `TerminalQrEvent` or `PaymentStatusEvent` |
| `/topic/enterprise/{enterpriseCode}/payments` | Optional — final payment updates only (no PhonePe confirm prompt) | `PaymentStatusEvent` |

---

## Event Types

| `eventType` | When | Action |
|-------------|------|--------|
| `PAYMENT_SUCCESS` | `PAID_AUTO_VERIFIED` or `PAID_CONFIRMED_BY_CASHIER` | Show success screen |
| `PHONEPE_PAYMENT_CONFIRMATION_REQUIRED` | `PHONEPE_MATCHED_WAITING_CONFIRMATION` | Show Confirm / Reject panel |
| `PAYMENT_STATUS_UPDATED` | `WAITING`, `EXPIRED`, `REJECTED_BY_CASHIER` | Update UI accordingly |

---

## Terminal QR Event (on QR generate)

Published to `/topic/terminal/{terminalId}`:

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

---

# Part 6 — Quick Integration Checklist

### On Page Load
- [ ] `POST /api/enterprise/validate` — validate enterprise code
- [ ] `GET /api/device/terminals?enterpriseCode=...` — load terminal dropdown

### On Generate QR
- [ ] `POST /api/payment/qr/generate` with correct `sourceApp`
- [ ] Display `qrImageBase64` as QR image
- [ ] Save `paymentId` for WebSocket + polling
- [ ] Connect WebSocket and subscribe to `/topic/payment/{paymentId}`

### Google Pay Screen
- [ ] Listen for `eventType: "PAYMENT_SUCCESS"` on WebSocket
- [ ] Fallback: poll `GET /api/payment/status/{paymentId}` every 3–5 sec
- [ ] No confirm/reject UI needed

### PhonePe Screen
- [ ] Listen for `eventType: "PHONEPE_PAYMENT_CONFIRMATION_REQUIRED"`
- [ ] Show Confirm / Reject panel with `amount`, `payerName`, `notificationId`
- [ ] On Confirm → `POST /api/payments/{paymentId}/phonepe/confirm`
- [ ] On Reject → `POST /api/payments/{paymentId}/phonepe/reject`
- [ ] Fallback: poll status endpoint; show panel when status = `PHONEPE_MATCHED_WAITING_CONFIRMATION`

### Do NOT Call from Web
- `POST /api/payment/notify` — Android only
- `POST /api/device/register` — Android only
- `POST /api/device/login` — Android only

---

# Appendix — Enterprise Setup APIs (Admin Screen)

Only needed if building an enterprise onboarding / admin page.

## Get Departments

```
GET /api/enterprise/departments
```

**Response (`data`):**
```json
[
  { "department": "PADM", "departmentCode": 1 },
  { "department": "INFINITY", "departmentCode": 2 },
  { "department": "INSIGHT", "departmentCode": 3 }
]
```

## Create Enterprise

```
POST /api/enterprise/create
```

**Request:**
```json
{
  "enterpriseCode": "PADM001",
  "enterpriseName": "PADM Enterprise",
  "department": "PADM",
  "departmentCode": 1,
  "liveFrom": "2026-06-25T00:00:00.000+00:00"
}
```
