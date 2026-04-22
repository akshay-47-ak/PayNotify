package com.acme.PayNotify.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Entity
@Table(name = "enterprise_master")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnterpriseMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "enterprise_code", unique = true, nullable = false, length = 50)
    private String enterpriseCode;

    @Column(name = "enterprise_name", nullable = false, length = 200)
    private String enterpriseName;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "live_from")
    private Timestamp liveFrom;

    @Column(name = "created_at", nullable = false)
    private Timestamp createdAt;
}