package br.com.patasunidas.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class InstitutionalHttpException(val status: Int) : IOException()
class InstitutionalApi(private val baseUrl: String) {
    suspend fun request(method: String, path: String, token: String, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val connection = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 5000; connection.readTimeout = 15000
            connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            if (connection.responseCode !in 200..299) throw InstitutionalHttpException(connection.responseCode)
            if (connection.responseCode == 204) JSONObject() else connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        } finally { connection.disconnect() }
    }
}

fun institutionalMessage(status: Int): String = when(status) {
    400 -> "Confira os campos informados."
    401 -> "Sessão expirada. Entre novamente."
    403 -> "Sua conta não tem permissão para esta operação."
    404 -> "Registro indisponível neste contexto."
    409 -> "A operação está em conflito com os dados atuais ou as regras de acesso. Recarregue os dados antes de tentar novamente."
    429 -> "Muitas tentativas. Aguarde antes de tentar novamente."
    else -> "Não foi possível concluir a operação. Tente novamente."
}

fun canManageEmployees(profile: UserProfile) = profile == UserProfile.ADMINISTRADOR
