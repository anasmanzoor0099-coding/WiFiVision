package com.wifivision.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiContractUtilsTest {

    @Test
    fun validateBaseUrl_enforcesHttpsInReleaseAndAllowsDevHttpInDebug() {
        val debugEmulator = ApiContractUtils.validateBaseUrl("http://10.0.2.2:5000/", allowCleartext = true)
        assertTrue(debugEmulator is UrlValidationResult.Valid)
        assertEquals("http://10.0.2.2:5000", (debugEmulator as UrlValidationResult.Valid).normalizedUrl)

        val releaseHttp = ApiContractUtils.validateBaseUrl("http://10.0.2.2:5000", allowCleartext = false)
        assertTrue(releaseHttp is UrlValidationResult.Invalid)

        val releaseHttps = ApiContractUtils.validateBaseUrl("https://api.wifivision.example/", allowCleartext = false)
        assertTrue(releaseHttps is UrlValidationResult.Valid)

        assertTrue(ApiContractUtils.validateBaseUrl("http://user:pass@10.0.2.2:5000", allowCleartext = true) is UrlValidationResult.Invalid)
        assertTrue(ApiContractUtils.validateBaseUrl("http://10.0.2.2:5000?foo=bar", allowCleartext = true) is UrlValidationResult.Invalid)
        assertTrue(ApiContractUtils.validateBaseUrl("ftp://10.0.2.2:5000", allowCleartext = true) is UrlValidationResult.Invalid)
        assertTrue(ApiContractUtils.validateBaseUrl("", allowCleartext = true) is UrlValidationResult.Invalid)
    }

    @Test
    fun parseRetryAfterSeconds_parsesPositiveIntegersOnly() {
        assertEquals(120L, ApiContractUtils.parseRetryAfterSeconds("120"))
        assertNull(ApiContractUtils.parseRetryAfterSeconds("0"))
        assertNull(ApiContractUtils.parseRetryAfterSeconds("-5"))
        assertNull(ApiContractUtils.parseRetryAfterSeconds("not-a-number"))
        assertNull(ApiContractUtils.parseRetryAfterSeconds(null))
    }
}
