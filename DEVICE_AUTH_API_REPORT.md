# Device Register/Login API Report

## Register Device

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

## Login Device

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

## Database Change

The `user_device` table now has this new column:

```sql
password_hash varchar(255) null
```

The column is nullable so existing devices do not break during schema update. Existing devices that do not have a password hash should be registered again with the same enterprise code, device identifier, role, device name, and new password before they can log in with device name and password.
