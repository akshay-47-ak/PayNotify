# PayNotify Change Report

This file is the running report for application changes. Add every new change at the top with the current date and time, followed by affected files, API request/response changes, database changes, validation behavior, and verification results.

## 2026-06-25 11:49:46 IST - Department Dropdown API

### Summary

Added a backend endpoint for the UI department dropdown so the application can fetch the allowed department names and codes from the server.

### Affected Files

- `src/main/java/com/acme/PayNotify/controller/EnterpriseController.java`
- `src/main/java/com/acme/PayNotify/dto/DepartmentResponse.java`
- `src/main/java/com/acme/PayNotify/service/EnterpriseService.java`
- `CHANGE_REPORT.md`

### Behavior Changes

- Added `GET /api/enterprise/departments`.
- The endpoint returns the fixed department list used by enterprise creation validation.
- The UI can use `department` as the display/value label and `departmentCode` as the numeric code.
- Current dropdown options are `PADM=1`, `INFINITY=2`, and `INSIGHT=3`.

### Department Dropdown API

Endpoint:

```http
GET /api/enterprise/departments
```

Success response:

```json
{
  "success": true,
  "message": "Departments fetched successfully",
  "data": [
    {
      "department": "PADM",
      "departmentCode": 1
    },
    {
      "department": "INFINITY",
      "departmentCode": 2
    },
    {
      "department": "INSIGHT",
      "departmentCode": 3
    }
  ]
}
```

### UI Usage

Use the response `data` array to render the department dropdown. When creating an enterprise, send either the selected `department`, the selected `departmentCode`, or both values to `/api/enterprise/create`.

Recommended create request after dropdown selection:

```json
{
  "enterpriseCode": "PADM001",
  "enterpriseName": "PADM Enterprise",
  "department": "PADM",
  "departmentCode": 1,
  "liveFrom": "2026-06-25T00:00:00.000+00:00"
}
```

### Database Change

No database change for this entry.

### Verification

Command:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./mvnw test
```

Result:

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 2026-06-25 11:46:25 IST - User Entered Enterprise Code and Department Fields

### Summary

Updated enterprise creation so the client enters the `enterpriseCode` instead of the backend generating it. Added department tracking for the three server departments: `PADM`, `INFINITY`, and `INSIGHT`.

### Affected Files

- `src/main/java/com/acme/PayNotify/entity/EnterpriseMaster.java`
- `src/main/java/com/acme/PayNotify/dto/CreateEnterpriseRequest.java`
- `src/main/java/com/acme/PayNotify/dto/CreateEnterpriseResponse.java`
- `src/main/java/com/acme/PayNotify/dto/EnterpriseValidationResponse.java`
- `src/main/java/com/acme/PayNotify/service/EnterpriseService.java`
- `CHANGE_REPORT.md`

### Behavior Changes

- `/api/enterprise/create` no longer generates `enterpriseCode`.
- `enterpriseCode` is now required in the create-enterprise request.
- The backend trims and stores `enterpriseCode` in uppercase.
- Duplicate `enterpriseCode` values are rejected with `Enterprise code already exists`.
- Enterprise creation now stores `department` and `departmentCode`.
- Valid departments are `PADM`, `INFINITY`, and `INSIGHT`.
- Valid department codes are `1`, `2`, and `3`.
- Department mapping is fixed as `PADM=1`, `INFINITY=2`, `INSIGHT=3`.
- The create request may send `department`, `departmentCode`, or both.
- If both `department` and `departmentCode` are sent, they must match.
- Enterprise validation responses now include `department` and `departmentCode` when the enterprise exists.

### Create Enterprise API

Endpoint:

```http
POST /api/enterprise/create
Content-Type: application/json
```

Request body using department name:

```json
{
  "enterpriseCode": "PADM001",
  "enterpriseName": "PADM Enterprise",
  "department": "PADM",
  "liveFrom": "2026-06-25T00:00:00.000+00:00"
}
```

Request body using department code:

```json
{
  "enterpriseCode": "INFINITY001",
  "enterpriseName": "Infinity Enterprise",
  "departmentCode": 2,
  "liveFrom": "2026-06-25T00:00:00.000+00:00"
}
```

Request body using both department and department code:

```json
{
  "enterpriseCode": "INSIGHT001",
  "enterpriseName": "Insight Enterprise",
  "department": "INSIGHT",
  "departmentCode": 3,
  "liveFrom": "2026-06-25T00:00:00.000+00:00"
}
```

Success response:

```json
{
  "success": true,
  "message": "Enterprise created successfully",
  "data": {
    "id": 1,
    "enterpriseCode": "PADM001",
    "enterpriseName": "PADM Enterprise",
    "department": "PADM",
    "departmentCode": 1,
    "isActive": true,
    "liveFrom": "2026-06-25T00:00:00.000+00:00",
    "createdAt": "2026-06-25T06:16:25.000+00:00"
  }
}
```

Validation error response examples:

```json
{
  "success": false,
  "message": "Enterprise code is required",
  "data": null
}
```

```json
{
  "success": false,
  "message": "Invalid department. Allowed values are PADM, INFINITY or INSIGHT",
  "data": null
}
```

```json
{
  "success": false,
  "message": "Department and department code do not match",
  "data": null
}
```

### Validate Enterprise API

Endpoint:

```http
POST /api/enterprise/validate
Content-Type: application/json
```

Request body:

```json
{
  "enterpriseCode": "PADM001"
}
```

Success response:

```json
{
  "success": true,
  "message": "Enterprise validation completed",
  "data": {
    "valid": true,
    "enterpriseCode": "PADM001",
    "enterpriseName": "PADM Enterprise",
    "department": "PADM",
    "departmentCode": 1,
    "status": "VALID",
    "message": "Enterprise validated successfully"
  }
}
```

### Database Change

The `enterprise_master` table now has these new columns:

```sql
department varchar(50) null;
department_code int null;
```

The columns are nullable so existing enterprise rows do not break during schema update. New enterprise creation validates and stores these fields.

### Verification

Command:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./mvnw test
```

Result:

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 2026-06-25 11:40:03 IST - Device Registration Password and Device Name Login

### Summary

Updated device authentication so registration collects a password and login no longer requires enterprise code. Login now uses `deviceName` and `password`.

### Affected Files

- `src/main/java/com/acme/PayNotify/dto/DeviceRegistrationRequest.java`
- `src/main/java/com/acme/PayNotify/dto/DeviceLoginRequest.java`
- `src/main/java/com/acme/PayNotify/entity/UserDevice.java`
- `src/main/java/com/acme/PayNotify/repository/UserDeviceRepository.java`
- `src/main/java/com/acme/PayNotify/service/DeviceRegistrationService.java`
- `CHANGE_REPORT.md`

### Behavior Changes

- Registration still validates `enterpriseCode`.
- Registration now requires `password`.
- Registration now requires `deviceName`, and `deviceName` must be unique because login uses it.
- Password is stored as a PBKDF2 hash in `user_device.password_hash`; the plain password is not stored.
- Login now requires only `deviceName` and `password`.
- Login no longer accepts or requires `enterpriseCode`.
- Login no longer accepts or requires `deviceIdentifier`.
- Existing devices without a password hash must be registered again with the same enterprise code, device identifier, role, device name, and a new password before they can log in with device name and password.

### Register Device API

Endpoint:

```http
POST /api/device/register
Content-Type: application/json
```

Request body:

```json
{
  "enterpriseCode": "ENT12345",
  "deviceName": "Counter 1",
  "role": "CASHIER",
  "deviceIdentifier": "android-device-unique-id",
  "password": "device-password"
}
```

Required fields:

- `enterpriseCode`: Used only during registration to validate the enterprise.
- `deviceName`: Required and must be unique because login uses this field.
- `role`: Allowed values are `OWNER` or `CASHIER`.
- `deviceIdentifier`: Unique device identifier for the physical device.
- `password`: Required. The backend stores a password hash, not the plain password.

Success response:

```json
{
  "success": true,
  "message": "Device registration completed",
  "data": {
    "deviceId": 1,
    "enterpriseCode": "ENT12345",
    "enterpriseName": "Demo Enterprise",
    "role": "CASHIER",
    "terminalId": "TERM-1782360000000",
    "deviceIdentifier": "android-device-unique-id",
    "deviceName": "Counter 1",
    "status": "REGISTERED"
  }
}
```

Already registered response:

```json
{
  "success": true,
  "message": "Device registration completed",
  "data": {
    "deviceId": 1,
    "enterpriseCode": "ENT12345",
    "enterpriseName": "Demo Enterprise",
    "role": "CASHIER",
    "terminalId": "TERM-1782360000000",
    "deviceIdentifier": "android-device-unique-id",
    "deviceName": "Counter 1",
    "status": "ALREADY_REGISTERED"
  }
}
```

Validation error response examples:

```json
{
  "success": false,
  "message": "Password is required",
  "data": null
}
```

```json
{
  "success": false,
  "message": "Device name is already registered",
  "data": null
}
```

### Login Device API

Endpoint:

```http
POST /api/device/login
Content-Type: application/json
```

Request body:

```json
{
  "deviceName": "Counter 1",
  "password": "device-password"
}
```

Required fields:

- `deviceName`: Registered device name.
- `password`: Registered device password.

Removed login fields:

- `enterpriseCode` is no longer required for login.
- `deviceIdentifier` is no longer required for login.

Success response:

```json
{
  "success": true,
  "message": "Device login successful",
  "data": {
    "deviceId": 1,
    "enterpriseCode": "ENT12345",
    "enterpriseName": "Demo Enterprise",
    "role": "CASHIER",
    "terminalId": "TERM-1782360000000",
    "deviceIdentifier": "android-device-unique-id",
    "deviceName": "Counter 1",
    "status": "LOGIN_SUCCESS"
  }
}
```

Invalid login response:

```json
{
  "success": false,
  "message": "Invalid device name or password",
  "data": null
}
```

Inactive device response:

```json
{
  "success": false,
  "message": "Device is inactive. Please contact admin.",
  "data": null
}
```

### Database Change

The `user_device` table now has this new column:

```sql
password_hash varchar(255) null
```

The column is nullable so existing devices do not break during schema update. Existing devices that do not have a password hash should be registered again with the same enterprise code, device identifier, role, device name, and new password before they can log in with device name and password.

### Verification

Command:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./mvnw test
```

Result:

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Note:

The system `java` command points to Java 8, but this Spring Boot 4 project requires a newer JDK. Verification was run with `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64`.
