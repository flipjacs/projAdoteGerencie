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

class AuthApi(private val baseUrl: String) {
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

    suspend fun login(email: String, senha: String): AuthSession = withContext(Dispatchers.IO) {
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
                else -> throw IllegalStateException("Erro HTTP ${connection.responseCode}")
            }
        }
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
