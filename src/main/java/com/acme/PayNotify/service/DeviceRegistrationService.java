/*
 * File: DeviceRegistrationService.java
 * Created: 2026-04-22
 * Author: Akshay Athavale
 * Use: Contains business logic used by PayNotify API and WebSocket flows.
 */
package com.acme.PayNotify.service;

import com.acme.PayNotify.dto.DeviceLoginRequest;
import com.acme.PayNotify.dto.DeviceRegistrationRequest;
import com.acme.PayNotify.dto.DeviceRegistrationResponse;
import com.acme.PayNotify.dto.TerminalResponse;
import com.acme.PayNotify.entity.EnterpriseMaster;
import com.acme.PayNotify.entity.UserDevice;
import com.acme.PayNotify.repository.UserDeviceRepository;
import com.acme.PayNotify.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
public class DeviceRegistrationService {

    private static final String PASSWORD_HASH_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int PASSWORD_HASH_ITERATIONS = 65536;
    private static final int PASSWORD_HASH_KEY_LENGTH = 256;
    private static final int PASSWORD_SALT_LENGTH = 16;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Autowired
    private EnterpriseService enterpriseService;

    @Autowired
    private UserDeviceRepository userDeviceRepository;

    @Autowired
    private JwtService jwtService;

    public DeviceRegistrationResponse registerDevice(DeviceRegistrationRequest request) {

        if (request == null) {
            throw new RuntimeException("Device registration request is required");
        }

        if (request.getEnterpriseCode() == null || request.getEnterpriseCode().trim().isEmpty()) {
            throw new RuntimeException("Enterprise code is required");
        }

        if (request.getRole() == null || request.getRole().trim().isEmpty()) {
            throw new RuntimeException("Role is required");
        }

        if (request.getDeviceIdentifier() == null || request.getDeviceIdentifier().trim().isEmpty()) {
            throw new RuntimeException("Device identifier is required");
        }

        if (request.getDeviceName() == null || request.getDeviceName().trim().isEmpty()) {
            throw new RuntimeException("Device name is required");
        }

        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new RuntimeException("Password is required");
        }

        String role = request.getRole().trim().toUpperCase();
        if (!"OWNER".equals(role) && !"CASHIER".equals(role)) {
            throw new RuntimeException("Invalid role. Allowed values are OWNER or CASHIER");
        }

        EnterpriseMaster enterprise =
                enterpriseService.getValidatedEnterprise(request.getEnterpriseCode());

        String deviceIdentifier = normalizeDeviceIdentifier(request.getDeviceIdentifier());
        String deviceName = request.getDeviceName().trim();

        UserDevice existingDevice = findExistingDeviceForRegistration(enterprise, deviceIdentifier);

        validateDeviceNameIsAvailable(deviceName, existingDevice);

        if (existingDevice != null) {

            if (existingDevice.getIsActive() == null || !existingDevice.getIsActive()) {
                throw new RuntimeException("Device is already registered but inactive. Please contact admin.");
            }

            if (existingDevice.getRole() != null && !existingDevice.getRole().equalsIgnoreCase(role)) {
                throw new RuntimeException(
                        "This device is already registered as " + existingDevice.getRole()
                                + ". Same device cannot be registered with another role."
                );
            }

            if (existingDevice.getDeviceName() == null || existingDevice.getDeviceName().trim().isEmpty()) {
                existingDevice.setDeviceName(deviceName);
            }

            if (existingDevice.getPasswordHash() == null || existingDevice.getPasswordHash().trim().isEmpty()) {
                existingDevice.setPasswordHash(hashPassword(request.getPassword()));
            }

            existingDevice.setDeviceIdentifier(deviceIdentifier);

            existingDevice = saveDevice(existingDevice);

            DeviceRegistrationResponse response = buildResponse(existingDevice, enterprise);
            response.setStatus("ALREADY_REGISTERED");

            return response;
        }

        UserDevice device = new UserDevice();
        device.setEnterprise(enterprise);
        device.setRole(role);
        device.setDeviceIdentifier(deviceIdentifier);
        device.setDeviceName(deviceName);
        device.setPasswordHash(hashPassword(request.getPassword()));
        device.setTerminalId(generateNextTerminalId());
        device.setIsActive(true);
        device.setCompCode(1);
        device.setTenantCode(1);

        device = saveDevice(device);

        DeviceRegistrationResponse response = buildResponse(device, enterprise);
        response.setStatus("REGISTERED");

        return response;
    }

    public DeviceRegistrationResponse loginDevice(DeviceLoginRequest request) {

        if (request == null) {
            throw new RuntimeException("Device login request is required");
        }

        if (request.getDeviceName() == null || request.getDeviceName().trim().isEmpty()) {
            throw new RuntimeException("Device name is required");
        }

        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new RuntimeException("Password is required");
        }

        List<UserDevice> devices = userDeviceRepository.findByDeviceName(request.getDeviceName().trim());

        if (devices.isEmpty()) {
            throw new RuntimeException("Invalid device name or password");
        }

        if (devices.size() > 1) {
            throw new RuntimeException("Device name is not unique. Please contact admin.");
        }

        UserDevice device = devices.get(0);

        if (device.getIsActive() == null || !device.getIsActive()) {
            throw new RuntimeException("Device is inactive. Please contact admin.");
        }

        if (device.getPasswordHash() == null || !verifyPassword(request.getPassword(), device.getPasswordHash())) {
            throw new RuntimeException("Invalid device name or password");
        }

        EnterpriseMaster enterprise = device.getEnterprise();
        enterpriseService.getValidatedEnterprise(enterprise.getEnterpriseCode());

        DeviceRegistrationResponse response = buildResponse(device, enterprise);
        response.setStatus("LOGIN_SUCCESS");

        return response;
    }

    public List<TerminalResponse> getActiveTerminals(String enterpriseCode) {

        if (enterpriseCode == null || enterpriseCode.trim().isEmpty()) {
            throw new RuntimeException("Enterprise code is required");
        }

        EnterpriseMaster enterprise =
                enterpriseService.getValidatedEnterprise(enterpriseCode);

        List<UserDevice> devices =
                userDeviceRepository.findByEnterpriseAndIsActiveTrue(enterprise);

        List<TerminalResponse> responseList = new ArrayList<>();

        for (UserDevice device : devices) {
            TerminalResponse response = new TerminalResponse();
            response.setDeviceId(device.getId());
            response.setEnterpriseCode(enterprise.getEnterpriseCode());
            response.setEnterpriseName(enterprise.getEnterpriseName());
            response.setTerminalId(device.getTerminalId());
            response.setRole(device.getRole());
            response.setDeviceIdentifier(device.getDeviceIdentifier());
            response.setDeviceName(device.getDeviceName());

            responseList.add(response);
        }

        return responseList;
    }

    public UserDevice getActiveDevice(String enterpriseCode, String deviceIdentifier) {
        EnterpriseMaster enterprise = enterpriseService.getValidatedEnterprise(enterpriseCode);
        String normalizedDeviceIdentifier = normalizeDeviceIdentifier(deviceIdentifier);

        UserDevice device = findDeviceByEnterpriseAndIdentifier(enterprise, normalizedDeviceIdentifier);

        if (device.getIsActive() == null || !device.getIsActive()) {
            throw new RuntimeException("Device is inactive");
        }

        return device;
    }

    public UserDevice getActiveTerminal(String enterpriseCode, String terminalId) {
        EnterpriseMaster enterprise = enterpriseService.getValidatedEnterprise(enterpriseCode);

        UserDevice device = userDeviceRepository
                .findByEnterpriseAndTerminalId(enterprise, terminalId)
                .orElseThrow(() -> new RuntimeException("Terminal not found for this enterprise"));

        if (device.getIsActive() == null || !device.getIsActive()) {
            throw new RuntimeException("Terminal device is inactive");
        }

        return device;
    }

    private String generateNextTerminalId() {
        return "TERM-" + System.currentTimeMillis();
    }

    private UserDevice findExistingDeviceForRegistration(
            EnterpriseMaster enterprise,
            String deviceIdentifier
    ) {
        List<UserDevice> devices = new ArrayList<>();
        devices.addAll(userDeviceRepository.findByDeviceIdentifier(deviceIdentifier));

        for (UserDevice device : userDeviceRepository.findAll()) {
            if (deviceIdentifier.equals(normalizeDeviceIdentifier(device.getDeviceIdentifier()))
                    && !containsDevice(devices, device)) {
                devices.add(device);
            }
        }

        if (devices.isEmpty()) {
            return null;
        }

        UserDevice deviceRegisteredToThisEnterprise = null;

        for (UserDevice device : devices) {
            EnterpriseMaster registeredEnterprise = device.getEnterprise();
            if (registeredEnterprise == null
                    || registeredEnterprise.getId() == null
                    || !registeredEnterprise.getId().equals(enterprise.getId())) {
                throw new RuntimeException("Device is already registered with another enterprise");
            }

            deviceRegisteredToThisEnterprise = device;
        }

        return deviceRegisteredToThisEnterprise;
    }

    private UserDevice findDeviceByEnterpriseAndIdentifier(
            EnterpriseMaster enterprise,
            String deviceIdentifier
    ) {
        UserDevice device = userDeviceRepository
                .findByEnterpriseAndDeviceIdentifier(enterprise, deviceIdentifier)
                .orElse(null);

        if (device != null) {
            return device;
        }

        for (UserDevice enterpriseDevice : userDeviceRepository.findByEnterprise(enterprise)) {
            if (deviceIdentifier.equals(normalizeDeviceIdentifier(enterpriseDevice.getDeviceIdentifier()))) {
                return enterpriseDevice;
            }
        }

        throw new RuntimeException("Device not registered for this enterprise");
    }

    private boolean containsDevice(List<UserDevice> devices, UserDevice candidate) {
        for (UserDevice device : devices) {
            if (device.getId() != null && device.getId().equals(candidate.getId())) {
                return true;
            }
        }

        return false;
    }

    private String normalizeDeviceIdentifier(String deviceIdentifier) {
        String trimmedDeviceIdentifier = deviceIdentifier.trim();
        int separatorIndex = trimmedDeviceIdentifier.indexOf('_');

        if (separatorIndex < 0 || separatorIndex == trimmedDeviceIdentifier.length() - 1) {
            return trimmedDeviceIdentifier;
        }

        return trimmedDeviceIdentifier.substring(separatorIndex + 1);
    }

    private void validateDeviceNameIsAvailable(String deviceName, UserDevice existingDevice) {
        List<UserDevice> devices = userDeviceRepository.findByDeviceName(deviceName);

        for (UserDevice device : devices) {
            if (existingDevice == null || !device.getId().equals(existingDevice.getId())) {
                throw new RuntimeException("Device name is already registered");
            }
        }
    }

    private UserDevice saveDevice(UserDevice device) {
        try {
            return userDeviceRepository.save(device);
        } catch (DataIntegrityViolationException e) {
            if (isDeviceNameConstraintViolation(e)) {
                throw new RuntimeException("Device name is already registered", e);
            }
            throw new RuntimeException("Device is already registered with another enterprise", e);
        }
    }

    private boolean isDeviceNameConstraintViolation(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause() == null
                ? e.getMessage()
                : e.getMostSpecificCause().getMessage();

        return message != null && message.toLowerCase().contains("device_name");
    }

    private String hashPassword(String password) {
        byte[] salt = new byte[PASSWORD_SALT_LENGTH];
        SECURE_RANDOM.nextBytes(salt);
        byte[] hash = generatePasswordHash(password, salt);

        return PASSWORD_HASH_ALGORITHM
                + ":"
                + PASSWORD_HASH_ITERATIONS
                + ":"
                + Base64.getEncoder().encodeToString(salt)
                + ":"
                + Base64.getEncoder().encodeToString(hash);
    }

    private boolean verifyPassword(String password, String passwordHash) {
        try {
            String[] parts = passwordHash.split(":");
            if (parts.length != 4 || !PASSWORD_HASH_ALGORITHM.equals(parts[0])) {
                return false;
            }

            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[3]);
            byte[] actualHash = generatePasswordHash(password, salt, iterations);

            if (actualHash.length != expectedHash.length) {
                return false;
            }

            int result = 0;
            for (int i = 0; i < actualHash.length; i++) {
                result |= actualHash[i] ^ expectedHash[i];
            }

            return result == 0;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private byte[] generatePasswordHash(String password, byte[] salt) {
        return generatePasswordHash(password, salt, PASSWORD_HASH_ITERATIONS);
    }

    private byte[] generatePasswordHash(String password, byte[] salt, int iterations) {
        try {
            KeySpec spec = new PBEKeySpec(
                    password.toCharArray(),
                    salt,
                    iterations,
                    PASSWORD_HASH_KEY_LENGTH
            );
            SecretKeyFactory factory = SecretKeyFactory.getInstance(PASSWORD_HASH_ALGORITHM);
            return factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("Unable to process password", e);
        }
    }

    private DeviceRegistrationResponse buildResponse(UserDevice device, EnterpriseMaster enterprise) {
        DeviceRegistrationResponse response = new DeviceRegistrationResponse();
        response.setDeviceId(device.getId());
        response.setEnterpriseCode(enterprise.getEnterpriseCode());
        response.setEnterpriseName(enterprise.getEnterpriseName());
        response.setRole(device.getRole());
        response.setTerminalId(device.getTerminalId());
        response.setDeviceIdentifier(device.getDeviceIdentifier());
        response.setDeviceName(device.getDeviceName());
        JwtService.TokenResponse token = jwtService.createToken(
                device.getDeviceName(),
                enterprise.getEnterpriseCode(),
                device.getRole(),
                "MOBILE"
        );
        response.setToken(token.token());
        response.setTokenExpiresAt(token.expiresAt());
        response.setTokenType("Bearer");

        return response;
    }
}
