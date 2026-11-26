package br.com.patasunidas.ui

import org.junit.Assert.*
import org.junit.Test

class PasswordValidationTest {
    @Test fun confirmacaoDeveCoincidir() {
        assertTrue(passwordsMatch("12345678", "12345678"))
        assertFalse(passwordsMatch("12345678", "12345679"))
    }

    @Test fun senhaRespeitaLimites() {
        assertFalse(validPassword("1234567"))
        assertTrue(validPassword("12345678"))
        assertTrue(validPassword("123456789012"))
        assertFalse(validPassword("        "))
        assertFalse(validPassword("é".repeat(37)))
    }
}
