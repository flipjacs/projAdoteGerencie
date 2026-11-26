package br.com.sysanimais.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.io.ClassPathResource

class ConfiguracaoFlywayTests {
    @Test
    fun `migrations automaticas sao desabilitadas por padrao`() {
        val config = YamlPropertySourceLoader().load("application", ClassPathResource("application.yml"))
        assertEquals(false, config.single().getProperty("spring.flyway.enabled"))
    }
}
