package com.acme.PayNotify.service;

import com.acme.PayNotify.dto.DeviceRegistrationRequest;
import com.acme.PayNotify.dto.DeviceRegistrationResponse;
import com.acme.PayNotify.entity.EnterpriseMaster;
import com.acme.PayNotify.entity.UserDevice;
import com.acme.PayNotify.repository.UserDeviceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;

@Service
public class DeviceRegistrationService {

    @Autowired
    private EnterpriseService enterpriseService;

    @Autowired
    private UserDeviceRepository userDeviceRepository;

    public DeviceRegistrationResponse registerDevice(DeviceRegistrationRequest request) {

        if (request == null) {
            throw new RuntimeException("Device registration request is required");
        }

        if (request.getRole() == null || request.getRole().trim().isEmpty()) {
            throw new RuntimeException("Role is required");
        }

        if (request.getDeviceIdentifier() == null || request.getDeviceIdentifier().trim().isEmpty()) {
            throw new RuntimeException("Device identifier is required");
        }

        String role = request.getRole().trim().toUpperCase();
        if (!"OWNER".equals(role) && !"CASHIER".equals(role)) {
            throw new RuntimeException("Invalid role. Allowed values are OWNER or CASHIER");
        }

        EnterpriseMaster enterprise = enterpriseService.getValidatedEnterprise(request.getEnterpriseCode());

        UserDevice existingDevice = userDeviceRepository
                .findByEnterpriseAndDeviceIdentifier(enterprise, request.getDeviceIdentifier().trim())
                .orElse(null);

        if (existingDevice != null) {
            existingDevice.setRole(role);
            existingDevice.setDeviceName(request.getDeviceName());
            existingDevice.setIsActive(true);

            existingDevice = userDeviceRepository.save(existingDevice);

            return buildResponse(existingDevice);
        }

        UserDevice device = new UserDevice();
        device.setEnterprise(enterprise);
        device.setRole(role);
        device.setDeviceIdentifier(request.getDeviceIdentifier().trim());
        device.setDeviceName(request.getDeviceName());
        device.setTerminalId(generateNextTerminalId(enterprise));
        device.setIsActive(true);
        device.setRegisteredAt(new Timestamp(System.currentTimeMillis()));

        device = userDeviceRepository.save(device);

        return buildResponse(device);
    }

    public UserDevice getActiveDevice(String enterpriseCode, String deviceIdentifier) {
        EnterpriseMaster enterprise = enterpriseService.getValidatedEnterprise(enterpriseCode);

        UserDevice device = userDeviceRepository
                .findByEnterpriseAndDeviceIdentifier(enterprise, deviceIdentifier)
                .orElseThrow(() -> new RuntimeException("Device not registered for this enterprise"));

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

    private String generateNextTerminalId(EnterpriseMaster enterprise) {
        long nextNumber = userDeviceRepository.count() + 1;
        return "TERM-" + nextNumber;
    }

    private DeviceRegistrationResponse buildResponse(UserDevice device) {
        DeviceRegistrationResponse response = new DeviceRegistrationResponse();
        response.setDeviceId(device.getId());
        response.setEnterpriseCode(device.getEnterprise().getEnterpriseCode());
        response.setEnterpriseName(device.getEnterprise().getEnterpriseName());
        response.setRole(device.getRole());
        response.setTerminalId(device.getTerminalId());
        response.setDeviceIdentifier(device.getDeviceIdentifier());
        response.setDeviceName(device.getDeviceName());
        response.setStatus("REGISTERED");
        return response;
    }
}