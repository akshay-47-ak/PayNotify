/*
 * File: EnterpriseControllerTest.java
 * Created: 2026-07-28
 * Author: Akshay Athavale
 * Use: Contains automated API tests for PayNotify enterprise setup endpoints.
 */
package com.acme.PayNotify.controller;

import com.acme.PayNotify.dto.CreateEnterpriseResponse;
import com.acme.PayNotify.dto.DepartmentResponse;
import com.acme.PayNotify.dto.EnterpriseValidationResponse;
import com.acme.PayNotify.service.EnterpriseService;
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
class EnterpriseControllerTest {

    @Mock
    private EnterpriseService enterpriseService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        EnterpriseController controller = new EnterpriseController();
        ReflectionTestUtils.setField(controller, "enterpriseService", enterpriseService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void departmentsApiReturnsSetupDepartments() throws Exception {
        when(enterpriseService.getDepartments())
                .thenReturn(Collections.singletonList(new DepartmentResponse("PADM", 1)));

        mockMvc.perform(get("/api/enterprise/departments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].department").value("PADM"));
    }

    @Test
    void createEnterpriseApiCreatesEnterpriseForAdminSetup() throws Exception {
        CreateEnterpriseResponse response = new CreateEnterpriseResponse();
        response.setId(1L);
        response.setEnterpriseCode("ENT");
        response.setEnterpriseName("Enterprise");
        response.setDepartment("PADM");
        response.setDepartmentCode(1);
        response.setIsActive(true);
        when(enterpriseService.createEnterprise(any())).thenReturn(response);

        mockMvc.perform(post("/api/enterprise/create")
                        .contentType("application/json")
                        .content("{\"enterpriseCode\":\"ENT\",\"enterpriseName\":\"Enterprise\",\"department\":\"PADM\",\"departmentCode\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.enterpriseCode").value("ENT"));
    }

    @Test
    void validateEnterpriseApiWorksForWebAndMobileFlows() throws Exception {
        EnterpriseValidationResponse response = new EnterpriseValidationResponse();
        response.setValid(true);
        response.setEnterpriseCode("ENT");
        response.setEnterpriseName("Enterprise");
        response.setStatus("VALID");
        when(enterpriseService.validateEnterprise("ENT")).thenReturn(response);

        mockMvc.perform(post("/api/enterprise/validate")
                        .contentType("application/json")
                        .content("{\"enterpriseCode\":\"ENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.valid").value(true));
    }
}
