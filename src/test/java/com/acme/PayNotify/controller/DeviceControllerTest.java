/*
 * File: DeviceControllerTest.java
 * Created: 2026-07-28
 * Author: Akshay Athavale
 * Use: Contains automated API tests for PayNotify mobile device and terminal endpoints.
 */
package com.acme.PayNotify.controller;

import com.acme.PayNotify.dto.DeviceRegistrationResponse;
import com.acme.PayNotify.dto.TerminalResponse;
import com.acme.PayNotify.service.DeviceRegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DeviceControllerTest {

    @Mock
    private DeviceRegistrationService deviceRegistrationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        DeviceController controller = new DeviceController();
        ReflectionTestUtils.setField(controller, "deviceRegistrationService", deviceRegistrationService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void registerDeviceApiRegistersMobileDevice() throws Exception {
        when(deviceRegistrationService.registerDevice(any())).thenReturn(deviceResponse());

        mockMvc.perform(post("/api/device/register")
                        .contentType("application/json")
                        .content("{\"enterpriseCode\":\"ENT\",\"role\":\"CASHIER\",\"deviceIdentifier\":\"DEVICE-1\",\"deviceName\":\"Counter 1\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.terminalId").value("TERM-1"));
    }

    @Test
    void loginDeviceApiLogsInMobileDevice() throws Exception {
        when(deviceRegistrationService.loginDevice(any())).thenReturn(deviceResponse());

        mockMvc.perform(post("/api/device/login")
                        .contentType("application/json")
                        .content("{\"deviceName\":\"Counter 1\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.deviceIdentifier").value("DEVICE-1"));
    }

    @Test
    void terminalsApiReturnsActiveTerminalsForWebCashier() throws Exception {
        TerminalResponse terminal = new TerminalResponse();
        terminal.setDeviceId(10L);
        terminal.setEnterpriseCode("ENT");
        terminal.setEnterpriseName("Enterprise");
        terminal.setTerminalId("TERM-1");
        terminal.setRole("CASHIER");
        terminal.setDeviceIdentifier("DEVICE-1");
        terminal.setDeviceName("Counter 1");
        when(deviceRegistrationService.getActiveTerminals("ENT")).thenReturn(Collections.singletonList(terminal));

        mockMvc.perform(get("/api/device/terminals").param("enterpriseCode", "ENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].terminalId").value("TERM-1"));
    }

    private DeviceRegistrationResponse deviceResponse() {
        return new DeviceRegistrationResponse(
                10L,
                "ENT",
                "Enterprise",
                "CASHIER",
                "TERM-1",
                "DEVICE-1",
                "Counter 1",
                "ACTIVE"
        );
    }
}
