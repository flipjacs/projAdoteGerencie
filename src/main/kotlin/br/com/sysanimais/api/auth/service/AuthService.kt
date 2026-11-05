package br.com.sysanimais.api.auth.service

import br.com.sysanimais.api.auth.dto.LoginRequest
import br.com.sysanimais.api.auth.dto.LoginResponse
import br.com.sysanimais.api.usuarios.repository.UsuarioRepository
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class AuthService(
    private val repository: UsuarioRepository,
    private val encoder: PasswordEncoder,
    private val jwtEncoder: JwtEncoder
) {
    fun login(request: LoginRequest): LoginResponse {
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
