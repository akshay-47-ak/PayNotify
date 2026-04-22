package com.acme.PayNotify.service;

import com.acme.PayNotify.dto.EnterpriseValidationResponse;
import com.acme.PayNotify.entity.EnterpriseMaster;
import com.acme.PayNotify.repository.EnterpriseMasterRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;

@Service
public class EnterpriseService {

    @Autowired
    private EnterpriseMasterRepository enterpriseMasterRepository;

    public EnterpriseValidationResponse validateEnterprise(String enterpriseCode) {
        EnterpriseValidationResponse response = new EnterpriseValidationResponse();

        if (enterpriseCode == null || enterpriseCode.trim().isEmpty()) {
            response.setValid(false);
            response.setStatus("INVALID_REQUEST");
            response.setMessage("Enterprise code is required");
            return response;
        }

        EnterpriseMaster enterprise = enterpriseMasterRepository
                .findByEnterpriseCode(enterpriseCode.trim())
                .orElse(null);

        if (enterprise == null) {
            response.setValid(false);
            response.setEnterpriseCode(enterpriseCode);
            response.setStatus("NOT_FOUND");
            response.setMessage("Enterprise not found");
            return response;
        }

        if (enterprise.getIsActive() == null || !enterprise.getIsActive()) {
            response.setValid(false);
            response.setEnterpriseCode(enterprise.getEnterpriseCode());
            response.setEnterpriseName(enterprise.getEnterpriseName());
            response.setStatus("INACTIVE");
            response.setMessage("Enterprise is inactive");
            return response;
        }

        Timestamp now = new Timestamp(System.currentTimeMillis());
        if (enterprise.getLiveFrom() != null && enterprise.getLiveFrom().after(now)) {
            response.setValid(false);
            response.setEnterpriseCode(enterprise.getEnterpriseCode());
            response.setEnterpriseName(enterprise.getEnterpriseName());
            response.setStatus("NOT_LIVE");
            response.setMessage("Enterprise is not live yet");
            return response;
        }

        response.setValid(true);
        response.setEnterpriseCode(enterprise.getEnterpriseCode());
        response.setEnterpriseName(enterprise.getEnterpriseName());
        response.setStatus("VALID");
        response.setMessage("Enterprise validated successfully");

        return response;
    }

    public EnterpriseMaster getValidatedEnterprise(String enterpriseCode) {
        if (enterpriseCode == null || enterpriseCode.trim().isEmpty()) {
            throw new RuntimeException("Enterprise code is required");
        }

        EnterpriseMaster enterprise = enterpriseMasterRepository
                .findByEnterpriseCode(enterpriseCode.trim())
                .orElseThrow(() -> new RuntimeException("Enterprise not found"));

        if (enterprise.getIsActive() == null || !enterprise.getIsActive()) {
            throw new RuntimeException("Enterprise is inactive");
        }

        Timestamp now = new Timestamp(System.currentTimeMillis());
        if (enterprise.getLiveFrom() != null && enterprise.getLiveFrom().after(now)) {
            throw new RuntimeException("Enterprise is not live yet");
        }

        return enterprise;
    }
}