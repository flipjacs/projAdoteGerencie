package br.com.sysanimais.api.security

import br.com.sysanimais.api.usuarios.repository.UsuarioRepository
import br.com.sysanimais.api.usuarios.dto.UsuarioResponse
import br.com.sysanimais.api.auth.service.LoginRateLimiter

import com.nimbusds.jose.jwk.source.ImmutableSecret
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jwt.*
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.SecurityFilterChain
import javax.crypto.spec.SecretKeySpec
import jakarta.servlet.DispatcherType
import br.com.sysanimais.api.ongs.repository.OngRepository
import br.com.sysanimais.api.ongs.domain.EstadoOng
import br.com.sysanimais.api.usuarios.domain.Perfil

class UsuarioAuthenticationToken(jwt: Jwt, val usuario: UsuarioResponse) :
    JwtAuthenticationToken(jwt, listOf(SimpleGrantedAuthority("ROLE_${usuario.perfil.name}")))

@Configuration
class SecurityConfig(private val repository: UsuarioRepository, private val ongs: OngRepository) {
    @Bean
    fun loginRateLimiter() = LoginRateLimiter()

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun jwtEncoder(@Value("\${app.jwt-secret}") secret: String): JwtEncoder {
        require(secret.toByteArray().size >= 32) { "JWT_SECRET precisa ter ao menos 32 bytes" }
        return NimbusJwtEncoder(ImmutableSecret(secret.toByteArray()))
    }

    @Bean
    fun jwtDecoder(@Value("\${app.jwt-secret}") secret: String): JwtDecoder {
        require(secret.toByteArray().size >= 32) { "JWT_SECRET precisa ter ao menos 32 bytes" }
        val key = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build()
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        val converter = org.springframework.core.convert.converter.Converter<Jwt, org.springframework.security.authentication.AbstractAuthenticationToken> { jwt ->
            val user = jwt.subject.toIntOrNull()?.let { repository.findById(it).orElse(null) }
            val tokenVersion = when(val v=jwt.claims["token_version"]) { null -> 0L; is Int -> v.toLong(); is Long -> v; else -> null }
            val interno = user?.perfil in setOf(Perfil.ADMINISTRADOR, Perfil.VOLUNTARIO, Perfil.VETERINARIO)
            val ongAtiva = !interno || (user?.ongId?.let { ongs.findById(it).orElse(null)?.situacao == EstadoOng.ATIVA } == true)
            if (user == null || !user.ativo || user.credencialPendente || tokenVersion != user.tokenVersao || !user.identidadeGlobalValida() || !ongAtiva) JwtAuthenticationToken(jwt, emptyList())
            else UsuarioAuthenticationToken(jwt, UsuarioResponse.from(user))
        }
        http.csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling { it.accessDeniedHandler { _, response, _ ->
                response.status = 403
                response.characterEncoding = "UTF-8"
                response.contentType = "application/problem+json"
                response.writer.write("""{"status":403,"code":"ACESSO_NEGADO","detail":"Acesso não autorizado."}""")
            } }
            .authorizeHttpRequests {
                it.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                    .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/usuarios/cadastro", "/api/auth/login", "/api/auth/primeiro-acesso/trocar-senha").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/auth/me").hasAnyRole("USUARIO", "ADMINISTRADOR", "VOLUNTARIO", "VETERINARIO", "ADMIN_PLATAFORMA")
                    .requestMatchers(HttpMethod.GET, "/api/public/ongs/*/transparencia", "/api/public/ongs/*/transparencia/*").permitAll()
                    .requestMatchers("/api/sponsorships", "/api/me/sponsorships", "/api/me/sponsorships/**").hasRole("USUARIO")
                    .requestMatchers("/api/ong/sponsorships", "/api/ong/sponsorships/**").hasAnyRole("ADMINISTRADOR", "VOLUNTARIO")
                    .requestMatchers("/api/ong/relatorios/financeiro").hasRole("ADMINISTRADOR")
                    .requestMatchers("/api/ong/transparencia", "/api/ong/transparencia/**").hasAnyRole("ADMINISTRADOR", "VOLUNTARIO")
                    .requestMatchers("/api/donations", "/api/me/donations", "/api/me/donations/**").hasRole("USUARIO")
                    .requestMatchers("/api/ong/donations", "/api/ong/donations/**", "/api/ong/finance/**").hasAnyRole("ADMINISTRADOR", "VOLUNTARIO")
                    .requestMatchers("/api/adoption-requests", "/api/me/adoption-requests", "/api/me/adoption-requests/**").hasRole("USUARIO")
                    .requestMatchers("/api/ong/adoption-requests", "/api/ong/adoption-requests/**").hasAnyRole("ADMINISTRADOR", "VOLUNTARIO")
                    .requestMatchers("/api/plataforma/**").hasRole("ADMIN_PLATAFORMA")
                    .requestMatchers(HttpMethod.POST, "/api/usuarios/internos").hasRole("ADMINISTRADOR")
                    .requestMatchers("/api/ong/funcionarios", "/api/ong/funcionarios/**").hasRole("ADMINISTRADOR")
                    .requestMatchers(HttpMethod.GET, "/api/me/animais-adotados/*/prontuarios", "/api/me/animais-adotados/*/prontuarios/*").hasRole("USUARIO")
                    .requestMatchers("/api/me/animais-adotados/**").denyAll()
                    .requestMatchers(HttpMethod.POST, "/api/ong/animais/*/prontuarios").hasAnyRole("ADMINISTRADOR", "VETERINARIO")
                    .requestMatchers(HttpMethod.PATCH, "/api/ong/animais/*/prontuarios/*/corrigir").hasAnyRole("ADMINISTRADOR", "VETERINARIO")
                    .requestMatchers(HttpMethod.GET, "/api/ong/animais/*/prontuarios", "/api/ong/animais/*/prontuarios/*", "/api/ong/animais/*/prontuarios/*/correcoes").hasAnyRole("ADMINISTRADOR", "VOLUNTARIO", "VETERINARIO")
                    .requestMatchers("/api/ong/animais/*/prontuarios", "/api/ong/animais/*/prontuarios/**").denyAll()
                    .requestMatchers(HttpMethod.GET, "/api/animais", "/api/animais/*", "/api/animais/*/fotos/*").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/animais", "/api/ong/animais/*/fotos").hasAnyRole("ADMINISTRADOR", "VOLUNTARIO")
                    .requestMatchers(HttpMethod.PUT, "/api/ong/animais/*").hasAnyRole("ADMINISTRADOR", "VOLUNTARIO")
                    .requestMatchers(HttpMethod.PATCH, "/api/ong/animais/*/situacao", "/api/ong/animais/*/inativacao", "/api/ong/animais/*/publicar", "/api/ong/animais/*/despublicar").hasAnyRole("ADMINISTRADOR", "VOLUNTARIO")
                    .requestMatchers(HttpMethod.GET, "/api/ong/animais", "/api/ong/animais/**").hasAnyRole("ADMINISTRADOR", "VOLUNTARIO", "VETERINARIO")
                    .anyRequest().denyAll()
            }
            .oauth2ResourceServer { it.jwt { jwt -> jwt.jwtAuthenticationConverter(converter) } }
        return http.build()
    }
}
