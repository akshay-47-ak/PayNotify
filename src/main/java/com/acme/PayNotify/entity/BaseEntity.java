package com.acme.PayNotify.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity {

    @Column(name = "comp_code", nullable = false)
    private Integer compCode = 1;

    @Column(name = "tenant_code", nullable = false)
    private Integer tenantCode = 1;
}