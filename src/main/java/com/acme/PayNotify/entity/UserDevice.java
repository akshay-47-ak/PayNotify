package com.acme.PayNotify.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Entity
@Table(name = "user_device")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "enterprise_id", nullable = false)
    private EnterpriseMaster enterprise;

    @Column(name = "terminal_id", nullable = false, unique = true, length = 50)
    private String terminalId;

    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @Column(name = "device_identifier", nullable = false, unique = true, length = 200)
    private String deviceIdentifier;

    @Column(name = "device_name", length = 200)
    private String deviceName;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "registered_at", nullable = false)
    private Timestamp registeredAt;
}