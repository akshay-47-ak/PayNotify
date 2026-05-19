package com.acme.PayNotify.service;

import com.acme.PayNotify.dto.DeviceLoginRequest;
import com.acme.PayNotify.dto.DeviceRegistrationRequest;
import com.acme.PayNotify.dto.DeviceRegistrationResponse;
import com.acme.PayNotify.dto.TerminalResponse;
import com.acme.PayNotify.entity.EnterpriseMaster;
import com.acme.PayNotify.entity.UserDevice;
import com.acme.PayNotify.repository.UserDeviceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

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

        if (request.getEnterpriseCode() == null || request.getEnterpriseCode().trim().isEmpty()) {
            throw new RuntimeException("Enterprise code is required");
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

        EnterpriseMaster enterprise =
                enterpriseService.getValidatedEnterprise(request.getEnterpriseCode());

        String deviceIdentifier = request.getDeviceIdentifier().trim();

        UserDevice existingDevice = userDeviceRepository
                .findByEnterpriseAndDeviceIdentifier(enterprise, deviceIdentifier)
                .orElse(null);

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

            DeviceRegistrationResponse response = buildResponse(existingDevice, enterprise);
            response.setStatus("ALREADY_REGISTERED");

            return response;
        }

        UserDevice device = new UserDevice();
        device.setEnterprise(enterprise);
        device.setRole(role);
        device.setDeviceIdentifier(deviceIdentifier);
        device.setDeviceName(request.getDeviceName());
        device.setTerminalId(generateNextTerminalId());
        device.setIsActive(true);
        device.setRegisteredAt(new Timestamp(System.currentTimeMillis()));
        device.setCompCode(1);
        device.setTenantCode(1);

        device = userDeviceRepository.save(device);

        DeviceRegistrationResponse response = buildResponse(device, enterprise);
        response.setStatus("REGISTERED");

        return response;
    }

    public DeviceRegistrationResponse loginDevice(DeviceLoginRequest request) {

        if (request == null) {
            throw new RuntimeException("Device login request is required");
        }

        if (request.getEnterpriseCode() == null || request.getEnterpriseCode().trim().isEmpty()) {
            throw new RuntimeException("Enterprise code is required");
        }

        if (request.getDeviceIdentifier() == null || request.getDeviceIdentifier().trim().isEmpty()) {
            throw new RuntimeException("Device identifier is required");
        }

        EnterpriseMaster enterprise =
                enterpriseService.getValidatedEnterprise(request.getEnterpriseCode());

        UserDevice device = userDeviceRepository
                .findByEnterpriseAndDeviceIdentifier(
                        enterprise,
                        request.getDeviceIdentifier().trim()
                )
                .orElseThrow(() -> new RuntimeException("Device is not registered. Please register first."));

        if (device.getIsActive() == null || !device.getIsActive()) {
            throw new RuntimeException("Device is inactive. Please contact admin.");
        }

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

    private String generateNextTerminalId() {
        return "TERM-" + System.currentTimeMillis();
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

        return response;
    }
}