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

class UsuarioAuthenticationToken(jwt: Jwt, val usuario: UsuarioResponse) :
    JwtAuthenticationToken(jwt, listOf(SimpleGrantedAuthority("ROLE_${usuario.perfil.name}")))

@Configuration
class SecurityConfig(private val repository: UsuarioRepository) {
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
            if (user == null || !user.ativo) JwtAuthenticationToken(jwt, emptyList())
            else UsuarioAuthenticationToken(jwt, UsuarioResponse.from(user))
        }
        http.csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                    .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/usuarios/cadastro", "/api/auth/login").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/auth/me").hasAnyRole("USUARIO", "ADMINISTRADOR", "VOLUNTARIO", "VETERINARIO")
                    .requestMatchers(HttpMethod.POST, "/api/usuarios/internos").hasRole("ADMINISTRADOR")
                    .requestMatchers(HttpMethod.GET, "/api/animais", "/api/animais/*/fotos/*").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/animais", "/api/ong/animais/*/fotos").hasAnyRole("ADMINISTRADOR", "VOLUNTARIO")
                    .requestMatchers(HttpMethod.PUT, "/api/ong/animais/*").hasAnyRole("ADMINISTRADOR", "VOLUNTARIO")
                    .requestMatchers(HttpMethod.PATCH, "/api/ong/animais/*/situacao", "/api/ong/animais/*/inativacao").hasAnyRole("ADMINISTRADOR", "VOLUNTARIO")
                    .requestMatchers(HttpMethod.GET, "/api/ong/animais", "/api/ong/animais/**").hasAnyRole("ADMINISTRADOR", "VOLUNTARIO", "VETERINARIO")
                    .anyRequest().denyAll()
            }
            .oauth2ResourceServer { it.jwt { jwt -> jwt.jwtAuthenticationConverter(converter) } }
        return http.build()
    }
}
