package com.acme.PayNotify.controller;

import com.acme.PayNotify.dto.ApiResponse;
import com.acme.PayNotify.dto.DeviceRegistrationRequest;
import com.acme.PayNotify.dto.DeviceRegistrationResponse;
import com.acme.PayNotify.service.DeviceRegistrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/device")
@CrossOrigin(origins = "*")
public class DeviceController {

    @Autowired
    private DeviceRegistrationService deviceRegistrationService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<DeviceRegistrationResponse>> registerDevice(
            @RequestBody DeviceRegistrationRequest request) {
        try {
            DeviceRegistrationResponse response = deviceRegistrationService.registerDevice(request);

            return ResponseEntity.ok(
                    new ApiResponse<>(true, "Device registered successfully", response)
            );
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }
}