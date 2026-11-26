package br.com.sysanimais.api.config

import br.com.sysanimais.api.usuarios.domain.Perfil
import br.com.sysanimais.api.usuarios.domain.Usuario
import br.com.sysanimais.api.usuarios.repository.UsuarioRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.dao.DataIntegrityViolationException

@Service
class BootstrapGlobalService(private val db: JdbcTemplate, private val users: UsuarioRepository, private val encoder: PasswordEncoder) {
 @Transactional fun criar(nome: String,email: String,senha: String, schemaEsperado: String) {
  require(schemaEsperado.matches(Regex("[A-Za-z0-9_]{1,64}"))) { "Esquema esperado inválido" }
  require(db.queryForObject("SELECT DATABASE()",String::class.java)==schemaEsperado) { "Banco conectado não corresponde ao destino autorizado" }
  require(nome.isNotBlank() && nome.length<=100 && email.length<=120 && email.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) { "Identidade inválida" }
  require(senha.length in 12..72 && senha.isNotBlank()) { "Credencial inválida" }
  val concluido=db.queryForObject("SELECT concluido FROM bootstrap_global_guard WHERE id=1 FOR UPDATE",Boolean::class.java) ?: error("Controle de bootstrap indisponível")
  require(!concluido) { "Bootstrap já realizado" }
  require(db.queryForObject("SELECT COUNT(*) FROM usuario WHERE LOWER(perfil)='admin_plataforma'",Int::class.java)==0) { "Bootstrap já realizado" }
  val user=try { users.saveAndFlush(Usuario(nome=nome.trim(),email=email.trim().lowercase(),senhaHash=encoder.encode(senha),perfil=Perfil.ADMIN_PLATAFORMA,ongId=null)) }
   catch (_: DataIntegrityViolationException) { throw IllegalStateException("Não foi possível provisionar a conta global") }
  db.update("INSERT INTO auditoria_institucional(ator_id,operacao,estado_posterior) VALUES (?,?,?)",user.id,"BOOTSTRAP_GLOBAL","ATIVO")
  db.update("UPDATE bootstrap_global_guard SET concluido=true WHERE id=1")
 }
}

@Configuration
class BootstrapGlobalConfig {
 @Bean
 @ConditionalOnProperty(name=["app.bootstrap-global.enabled"], havingValue="true")
 fun bootstrapGlobal(service: BootstrapGlobalService, context: ConfigurableApplicationContext,
  @Value("\${server.port:8080}") port: Int,
  @Value("\${management.server.port:-1}") managementPort: Int,
  @Value("\${spring.flyway.enabled:false}") flyway: Boolean,
  @Value("\${DB_URL:}") dbUrl: String,
  @Value("\${app.bootstrap-global.confirm:}") confirm: String,
  @Value("\${app.bootstrap-global.schema:}") schema: String,
  @Value("\${app.bootstrap-global.nome:}") nome: String,
  @Value("\${app.bootstrap-global.email:}") email: String): ApplicationRunner = ApplicationRunner { _: ApplicationArguments ->
   require(port == -1 && managementPort == -1 && !flyway && confirm == "AUTORIZADO" && dbUrl.startsWith("jdbc:mysql://")) { "Bootstrap exige destino explícito, modo offline, Flyway desativado e autorização explícita" }
   val secret = System.console()?.readPassword("Senha inicial: ")?.concatToString()
    ?: System.`in`.bufferedReader().readLine() ?: error("Credencial não fornecida")
   service.criar(nome,email,secret,schema)
   SpringApplication.exit(context)
  }
}
