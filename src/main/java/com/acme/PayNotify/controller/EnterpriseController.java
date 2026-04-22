package com.acme.PayNotify.controller;

import com.acme.PayNotify.dto.ApiResponse;
import com.acme.PayNotify.dto.EnterpriseValidationRequest;
import com.acme.PayNotify.dto.EnterpriseValidationResponse;
import com.acme.PayNotify.service.EnterpriseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/enterprise")
@CrossOrigin(origins = "*")
public class EnterpriseController {

    @Autowired
    private EnterpriseService enterpriseService;

    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<EnterpriseValidationResponse>> validateEnterprise(
            @RequestBody EnterpriseValidationRequest request) {
        try {
            EnterpriseValidationResponse response =
                    enterpriseService.validateEnterprise(request.getEnterpriseCode());

            return ResponseEntity.ok(
                    new ApiResponse<>(true, "Enterprise validation completed", response)
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }
}