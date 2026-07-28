/*
 * File: DepartmentResponse.java
 * Created: 2026-04-13
 * Author: Akshay Athavale
 * Use: Defines request or response payloads exchanged by PayNotify API/WebSocket clients.
 */
package com.acme.PayNotify.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentResponse {

    private String department;
    private Integer departmentCode;
}
