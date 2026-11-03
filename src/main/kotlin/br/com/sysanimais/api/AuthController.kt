package br.com.sysanimais.api

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val repository: UsuarioRepository,
    private val encoder: PasswordEncoder,
    private val jwtEncoder: JwtEncoder
) {
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): LoginResponse {
        val user = repository.findByEmail(request.email.trim().lowercase())
        if (user == null || !user.ativo || !encoder.matches(request.senha, user.senhaHash)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais inválidas")
        }
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .subject(user.id.toString())
            .issuedAt(now)
            .expiresAt(now.plusSeconds(3600))
            .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        return LoginResponse(jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue)
    }
}
