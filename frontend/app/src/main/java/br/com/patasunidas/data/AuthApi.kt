package br.com.patasunidas.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AuthSession(val accessToken: String, val expiresAtMillis: Long)

class InvalidCredentialsException : Exception()
class EmailAlreadyUsedException : Exception()
class InvalidRegistrationException : Exception()
class LoginRateLimitedException(val retryAfterSeconds: Long) : Exception()

fun parseRetryAfterSeconds(value: String?): Long =
    value?.trim()?.toLongOrNull()?.takeIf { it > 0 }?.coerceAtMost(900) ?: 30

class AuthApi(private val baseUrl: String) : AuthGateway {
    suspend fun register(nome: String, email: String, senha: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("nome", nome).put("email", email).put("senha", senha)
        post("/api/usuarios/cadastro", body) { connection ->
            when (connection.responseCode) {
                HttpURLConnection.HTTP_CREATED -> Unit
                HttpURLConnection.HTTP_CONFLICT -> throw EmailAlreadyUsedException()
                HttpURLConnection.HTTP_BAD_REQUEST -> throw InvalidRegistrationException()
                else -> throw IllegalStateException("Erro HTTP ${connection.responseCode}")
            }
        }
    }

    override suspend fun login(email: String, senha: String): AuthSession = withContext(Dispatchers.IO) {
        val body = JSONObject().put("email", email).put("senha", senha)
        post("/api/auth/login", body) { connection ->
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val response = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
                    val token = response.getString("accessToken")
                    val tokenType = response.getString("tokenType")
                    val expiresIn = response.getLong("expiresIn")
                    if (token.isBlank() || !tokenType.equals("Bearer", ignoreCase = true) || expiresIn <= 0) {
                        throw IllegalStateException("Resposta de autenticação inválida")
                    }
                    AuthSession(token, System.currentTimeMillis() + expiresIn * 1_000)
                }
                HttpURLConnection.HTTP_UNAUTHORIZED -> throw InvalidCredentialsException()
                429 -> throw LoginRateLimitedException(parseRetryAfterSeconds(connection.getHeaderField("Retry-After")))
                else -> throw IllegalStateException("Erro HTTP ${connection.responseCode}")
            }
        }
    }

    override suspend fun me(token: String): UserIdentity = withContext(Dispatchers.IO) {
        val connection = (URL(baseUrl.trimEnd('/') + "/api/auth/me").openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val json = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
                    val profile = UserProfile.fromApi(json.getString("perfil")) ?: throw InvalidIdentityException()
                    val ongId = if (json.isNull("ongId")) null else json.getInt("ongId")
                    val id = json.getInt("id")
                    val name = json.getString("nome")
                    val email = json.getString("email")
                    if (id <= 0 || name.isBlank() || email.isBlank() || (ongId != null && ongId <= 0)) throw InvalidIdentityException()
                    UserIdentity(id, name, email, profile, ongId)
                }
                HttpURLConnection.HTTP_UNAUTHORIZED -> throw SessionUnauthorizedException()
                HttpURLConnection.HTTP_FORBIDDEN -> throw SessionForbiddenException()
                in 500..599 -> throw java.io.IOException("Serviço de identidade indisponível")
                else -> throw IllegalStateException("Falha ao consultar identidade")
            }
        } finally { connection.disconnect() }
    }

    private fun <T> post(path: String, body: JSONObject, response: (HttpURLConnection) -> T): T {
        val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            return response(connection)
        } finally {
            connection.disconnect()
        }
    }
}
