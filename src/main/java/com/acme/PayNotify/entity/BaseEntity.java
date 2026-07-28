/*
 * File: BaseEntity.java
 * Created: 2026-05-18
 * Author: Akshay Athavale
 * Use: Maps PayNotify database table data used by payment and enterprise flows.
 */
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