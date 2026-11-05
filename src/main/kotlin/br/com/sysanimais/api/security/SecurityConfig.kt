package br.com.sysanimais.api.security

import br.com.sysanimais.api.usuarios.repository.UsuarioRepository

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
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.web.SecurityFilterChain
import javax.crypto.spec.SecretKeySpec

@Configuration
class SecurityConfig(private val repository: UsuarioRepository) {
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
        val converter = JwtAuthenticationConverter()
        converter.setJwtGrantedAuthoritiesConverter { jwt ->
            val user = jwt.subject.toIntOrNull()?.let { repository.findById(it).orElse(null) }
            if (user == null || !user.ativo) emptyList()
            else listOf(SimpleGrantedAuthority("ROLE_${user.perfil.name}"))
        }
        http.csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.POST, "/api/usuarios/cadastro", "/api/auth/login").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/usuarios/internos").hasRole("ADMINISTRADOR")
                    .anyRequest().denyAll()
            }
            .oauth2ResourceServer { it.jwt { jwt -> jwt.jwtAuthenticationConverter(converter) } }
        return http.build()
    }
}
