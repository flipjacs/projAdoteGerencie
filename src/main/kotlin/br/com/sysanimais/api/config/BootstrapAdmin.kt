package br.com.sysanimais.api.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** O bootstrap legado criava funcionários ativos com senha fornecida pelo operador. */
@Configuration
class BootstrapAdmin {
 @Bean fun verificarBootstrapLegado(
  @Value("\${app.bootstrap.ong-id}") ongId: Int,
  @Value("\${app.bootstrap.name}") nome: String,
  @Value("\${app.bootstrap.email}") email: String,
  @Value("\${app.bootstrap.password}") senha: String
 ) = ApplicationRunner {
  require(ongId==0 && nome.isBlank() && email.isBlank() && senha.isBlank()) {
   "Bootstrap legado de ONG desativado; provisionamento de credenciais depende da B1.4"
  }
 }
}
