# PhonePe Assisted Verification Change Report

## 2026-06-26 - PhonePe Payment Notification Support

### 2026-06-26 17:08 IST - Confirm API MySQL Lock Fix

Fixed a MySQL 5.7 syntax error in the PhonePe confirm API.

Issue:

```text
You have an error in your SQL syntax near 'of pr1_0'
```

Cause:

Hibernate generated `FOR UPDATE OF alias` for pessimistic locking. MySQL 5.7 supports `FOR UPDATE`, but not `FOR UPDATE OF alias`.

Fix:

- Changed `PaymentRequestRepository.findByIdForUpdate` to use native SQL:

```sql
select * from payment_request where id = :id for update
```

- Changed `PaymentNotificationLogRepository.findByIdForUpdate` to use native SQL:

```sql
select * from payment_notification_log where id = :id for update
```

Verification:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./mvnw -q -DskipTests compile
JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./mvnw -q test
```

Result:

```text
BUILD SUCCESS
```

### Summary

Added PhonePe support using assisted verification while preserving the existing Google Pay auto-verification flow.

Google Pay continues to use transaction reference / note based matching from the notification and is marked paid automatically. PhonePe notifications do not include the QR transaction reference, so PhonePe is matched only by terminal, amount, and active QR time window. A matched PhonePe payment waits for cashier confirmation before it is marked paid.

### Business Rule Implemented

- Google Pay can be auto-marked as paid only when the notification contains the QR transaction reference.
- PhonePe is never marked paid from notification amount alone.
- PhonePe moves to `PHONEPE_MATCHED_WAITING_CONFIRMATION` after a single assisted match.
- Cashier confirmation is required before PhonePe moves to `PAID_CONFIRMED_BY_CASHIER`.
- One terminal cannot have multiple active payment requests at the same time.

### Affected Files

- `src/main/java/com/acme/PayNotify/entity/PaymentRequest.java`
- `src/main/java/com/acme/PayNotify/entity/PaymentNotificationLog.java`
- `src/main/java/com/acme/PayNotify/dto/GenerateQrRequest.java`
- `src/main/java/com/acme/PayNotify/dto/PaymentNotificationRequest.java`
- `src/main/java/com/acme/PayNotify/dto/PaymentStatusEvent.java`
- `src/main/java/com/acme/PayNotify/dto/PhonePeConfirmRequest.java`
- `src/main/java/com/acme/PayNotify/dto/PhonePeRejectRequest.java`
- `src/main/java/com/acme/PayNotify/repository/PaymentRequestRepository.java`
- `src/main/java/com/acme/PayNotify/repository/PaymentNotificationLogRepository.java`
- `src/main/java/com/acme/PayNotify/service/PaymentService.java`
- `src/main/java/com/acme/PayNotify/service/PaymentWebSocketService.java`
- `src/main/java/com/acme/PayNotify/controller/PaymentController.java`
- `src/main/java/com/acme/PayNotify/controller/PaymentPhonePeController.java`
- `src/main/resources/application.properties`
- `src/main/resources/db/migration/V2__phonepe_assisted_verification.sql`
- `src/test/java/com/acme/PayNotify/service/PaymentServiceTest.java`

### Payment Status Changes

Added or used these statuses:

- `WAITING`
- `PAID_AUTO_VERIFIED`
- `PHONEPE_MATCHED_WAITING_CONFIRMATION`
- `PAID_CONFIRMED_BY_CASHIER`
- `UNMATCHED_NOTIFICATION`
- `AMBIGUOUS_NOTIFICATION`
- `EXPIRED`
- `REJECTED_BY_CASHIER`
- `USED_CONFIRMED`
- `MATCHED_WAITING_CONFIRMATION`
- `DUPLICATE`

Existing `PENDING` payment rows are still treated as waiting for backward compatibility.

### QR Generation Behavior

Before generating a new QR for a terminal, the backend now checks for active payment attempts on that terminal.

Active statuses:

- `WAITING`
- `PENDING`
- `PHONEPE_MATCHED_WAITING_CONFIRMATION`

If an active request exists, QR generation is rejected with:

```text
Selected terminal already has an active payment request.
```

New QR requests now store:

- `expiresAt`
- `cashierId`
- `cashierSessionId`
- `branchId`

Configuration:

```properties
payment.qr.expiry-minutes=15
payment.phonepe.notification-grace-minutes=10
```

### Notification Processing

Notification processing now always writes a `PaymentNotificationLog` before provider-specific processing.

The request DTO supports:

- `terminalId`
- `appName`
- `packageName`
- `amount`
- `payerName`
- `rawTitle`
- `rawMessage`
- `extractedTxnId`
- `notificationReceivedAt`

Duplicate detection uses a SHA-256 dedupe hash created from:

- normalized terminal ID
- app name
- package name
- amount
- raw title
- raw message
- notification time rounded to the minute

Duplicate notifications return `DUPLICATE` and are not processed again.

### Google Pay Flow

Google Pay behavior remains transaction-reference based:

1. Notification is parsed for transaction reference.
2. Backend finds a waiting payment by transaction reference.
3. Backend marks payment `PAID_AUTO_VERIFIED`.
4. Backend stores notification log as `USED_CONFIRMED`.
5. Existing websocket success update is published.

PhonePe does not use this auto-paid path.

### PhonePe Flow

PhonePe notification processing:

1. Validate `terminalId` and `amount`.
2. Find waiting payment attempts for the same terminal and amount.
3. Match only when:
   - terminal ID matches.
   - amount matches as `BigDecimal`.
   - payment status is `WAITING` or legacy `PENDING`.
   - payment was created before notification time.
   - notification time is within `expiresAt + grace minutes`.
4. If no match, notification becomes `UNMATCHED_NOTIFICATION`.
5. If one match, payment becomes `PHONEPE_MATCHED_WAITING_CONFIRMATION`.
6. If multiple matches, notification becomes `AMBIGUOUS_NOTIFICATION`.

Matched PhonePe websocket event:

```json
{
  "eventType": "PHONEPE_PAYMENT_CONFIRMATION_REQUIRED",
  "paymentId": "PAY-...",
  "notificationId": 501,
  "terminalId": "TERM-...",
  "amount": 500.00,
  "payerName": "Rahul",
  "message": "PhonePe payment received. Please confirm after checking customer."
}
```

### PhonePe Confirmation API

Endpoint:

```http
POST /api/payments/{paymentAttemptId}/phonepe/confirm
```

Request:

```json
{
  "cashierId": 10,
  "notificationId": 501
}
```

Behavior:

- Locks the payment attempt and notification row with pessimistic write locks.
- Validates payment status is `PHONEPE_MATCHED_WAITING_CONFIRMATION`.
- Validates notification ID, amount, and terminal ID.
- Rejects already-used notifications.
- Marks payment as `PAID_CONFIRMED_BY_CASHIER`.
- Sets `confirmedBy`, `confirmedAt`, and `paidAt`.
- Marks notification as `USED_CONFIRMED`.
- Publishes existing payment success websocket event.

Success response message:

```text
PhonePe payment confirmed successfully.
```

### PhonePe Reject API

Endpoint:

```http
POST /api/payments/{paymentAttemptId}/phonepe/reject
```

Request:

```json
{
  "cashierId": 10,
  "notificationId": 501,
  "reason": "Not my customer"
}
```

Behavior:

- Validates payment is waiting for PhonePe confirmation.
- Validates matched notification ID.
- Clears `matchedNotificationId`.
- If QR is still valid, payment returns to `WAITING`.
- If QR is expired, payment becomes `EXPIRED`.
- Notification is marked `REJECTED_BY_CASHIER`.
- Notification log is retained.

### Database Changes

Migration file:

```text
src/main/resources/db/migration/V2__phonepe_assisted_verification.sql
```

`payment_request` additions:

- `cashier_id`
- `cashier_session_id`
- `branch_id`
- `expires_at`
- `paid_at`
- `confirmed_at`
- `confirmed_by`
- `matched_notification_id`
- status column widened to `VARCHAR(50)`

`payment_notification_log` additions:

- `terminal_id`
- `app_name`
- `raw_title`
- `raw_message`
- `extracted_txn_id`
- `amount`
- `notification_received_at`
- `matched_payment_attempt_id`
- `status`
- `dedupe_hash`

Indexes:

- `idx_payment_attempt_terminal_status`
- `idx_payment_attempt_txn_id`
- `idx_payment_attempt_cashier_session`
- `idx_payment_attempt_created_at`
- `idx_notification_terminal_app_time`
- `idx_notification_dedupe_hash`
- `idx_notification_matched_attempt`
- `unique_dedupe_hash`

### Tests Added

Added focused service tests for:

- Google Pay auto verification by transaction reference.
- PhonePe single match moves to cashier confirmation.
- Duplicate notification is not processed again.
- Second PhonePe confirmation fails.
- QR generation rejects a second active request for the same terminal.

Test file:

```text
src/test/java/com/acme/PayNotify/service/PaymentServiceTest.java
```

### Verification

Compile:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./mvnw -q -DskipTests compile
```

Result:

```text
BUILD SUCCESS
```

Tests:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./mvnw -q test
```

Result:

```text
BUILD SUCCESS
```

### Notes

- The default `java` command on this machine points to Java 8, which cannot compile the Spring Boot 4 dependency set.
- Verification was done with installed JDK 25.
- `EnterpriseService` switch expressions were converted to ordinary switch statements so the project compiles cleanly in the current Maven/JDK environment.
