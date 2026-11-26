package br.com.sysanimais.api

import br.com.sysanimais.api.config.BootstrapAdmin
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.boot.DefaultApplicationArguments

class BootstrapAdminLegadoTests {
 @Test fun `bootstrap antigo falha fechado quando configurado`() {
  val args=DefaultApplicationArguments()
  assertDoesNotThrow { BootstrapAdmin().verificarBootstrapLegado(0,"","","").run(args) }
  assertThrows(IllegalArgumentException::class.java) { BootstrapAdmin().verificarBootstrapLegado(1,"Teste","teste@exemplo.invalid","SenhaForte123!").run(args) }
 }
}
