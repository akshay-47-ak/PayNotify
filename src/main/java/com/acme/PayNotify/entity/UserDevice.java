/*
 * File: UserDevice.java
 * Created: 2026-04-22
 * Author: Akshay Athavale
 * Use: Maps PayNotify database table data used by payment and enterprise flows.
 */
package com.acme.PayNotify.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.sql.Timestamp;

@Entity
@Table(
        name = "user_device",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_device_device_identifier",
                        columnNames = {"device_identifier"}
                ),
                @UniqueConstraint(
                        name = "uk_user_device_terminal_id",
                        columnNames = {"terminal_id"}
                ),
                @UniqueConstraint(
                        name = "uk_user_device_device_name",
                        columnNames = {"device_name"}
                )
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDevice extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "enterprise_id", nullable = false)
    private EnterpriseMaster enterprise;

    @Column(name = "terminal_id", nullable = false, length = 50)
    private String terminalId;

    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @Column(name = "device_identifier", nullable = false, unique = true, length = 200)
    private String deviceIdentifier;

    @Column(name = "device_name", unique = true, length = 200)
    private String deviceName;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "registered_at", nullable = false, updatable = false)
    private Timestamp registeredAt;
}
