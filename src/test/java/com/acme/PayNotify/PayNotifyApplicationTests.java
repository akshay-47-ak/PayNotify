/*
 * File: PayNotifyApplicationTests.java
 * Created: 2026-04-13
 * Author: Akshay Athavale
 * Use: Contains automated tests for PayNotify behavior.
 */
package com.acme.PayNotify;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class PayNotifyApplicationTests {

	@Test
	void applicationEntryPointExists() {
		assertNotNull(PayNotifyApplication.class);
	}

}
