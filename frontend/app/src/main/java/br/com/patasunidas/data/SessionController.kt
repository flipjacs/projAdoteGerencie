package br.com.patasunidas.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import java.io.IOException

enum class UserProfile {
    USUARIO, ADMINISTRADOR, VOLUNTARIO, VETERINARIO;
    companion object { fun fromApi(value: String): UserProfile? = entries.firstOrNull { it.name == value } }
}

data class UserIdentity(val id: Int, val nome: String, val email: String, val perfil: UserProfile, val ongId: Int?) {
    val isInternal get() = perfil != UserProfile.USUARIO
    val hasOng get() = ongId != null && ongId > 0
}

interface AuthGateway {
    suspend fun login(email: String, senha: String): AuthSession
    suspend fun me(token: String): UserIdentity
}

class SessionUnauthorizedException : Exception()
class SessionForbiddenException : Exception()
class InvalidIdentityException : Exception()

sealed interface SessionState {
    data class SignedOut(val message: String? = null, val retryAtMillis: Long? = null) : SessionState
    data object Loading : SessionState
    data class RetryIdentity(val session: AuthSession, val message: String) : SessionState
    data class Authenticated(val session: AuthSession, val identity: UserIdentity, val message: String? = null) : SessionState
}

class SessionController(private val api: AuthGateway, private val now: () -> Long = System::currentTimeMillis) {
    var state: SessionState by mutableStateOf(SessionState.SignedOut())
        private set
    private var generation = 0

    suspend fun login(email: String, senha: String) {
        val signedOut = state as? SessionState.SignedOut ?: return
        if (signedOut.retryAtMillis?.let { it > now() } == true) return
        val operation = ++generation
        state = SessionState.Loading
        try {
            val session = api.login(email, senha)
            if (operation == generation) identify(session, operation)
        } catch (e: CancellationException) { throw e }
        catch (_: InvalidCredentialsException) { if (operation == generation) state = SessionState.SignedOut("E-mail ou senha inválidos") }
        catch (e: LoginRateLimitedException) {
            if (operation == generation) {
                val seconds = e.retryAfterSeconds.coerceIn(1, 900)
                state = SessionState.SignedOut("Muitas tentativas. Tente novamente em aproximadamente $seconds segundos.", now() + seconds * 1000)
            }
        }
        catch (_: IOException) { if (operation == generation) state = SessionState.SignedOut("Não foi possível conectar à API. Tente novamente.") }
        catch (_: Exception) { if (operation == generation) state = SessionState.SignedOut("Falha no login. Tente novamente.") }
    }

    suspend fun retryIdentity() {
        val session = (state as? SessionState.RetryIdentity)?.session ?: return
        val operation = ++generation
        state = SessionState.Loading
        identify(session, operation)
    }

    private suspend fun identify(session: AuthSession, operation: Int) {
        if (session.expiresAtMillis <= now()) { if (operation == generation) logout("Sua sessão expirou. Entre novamente."); return }
        try {
            val identity = api.me(session.accessToken)
            if (operation == generation) {
                if (session.expiresAtMillis <= now()) logout("Sua sessão expirou. Entre novamente.")
                else state = SessionState.Authenticated(session, identity)
            }
        } catch (e: CancellationException) { throw e }
        catch (_: IOException) { if (operation == generation) state = SessionState.RetryIdentity(session, "Não foi possível consultar sua conta. Tente novamente.") }
        catch (_: SessionUnauthorizedException) { if (operation == generation) logout("Sessão inválida ou expirada. Entre novamente.") }
        catch (_: SessionForbiddenException) { if (operation == generation) logout("Não foi possível acessar esta conta. Entre novamente.") }
        catch (_: Exception) { if (operation == generation) logout("Não foi possível identificar sua conta. Entre novamente.") }
    }

    fun expireIfNeeded() {
        val session = when (val current = state) {
            is SessionState.Authenticated -> current.session
            is SessionState.RetryIdentity -> current.session
            else -> null
        }
        if (session != null && session.expiresAtMillis <= now()) logout("Sua sessão expirou. Entre novamente.")
    }

    suspend fun operationForbidden() {
        val current = state as? SessionState.Authenticated ?: return
        val operation = generation
        if (current.session.expiresAtMillis <= now()) { logout("Sua sessão expirou. Entre novamente."); return }
        try {
            val identity = api.me(current.session.accessToken)
            if (operation == generation) {
                if (current.session.expiresAtMillis <= now()) logout("Sua sessão expirou. Entre novamente.")
                else state = current.copy(identity = identity, message = "Você não tem permissão para esta operação.")
            }
        } catch (e: CancellationException) { throw e }
        catch (_: SessionUnauthorizedException) { if (operation == generation) logout("Sessão inválida ou expirada. Entre novamente.") }
        catch (_: SessionForbiddenException) { if (operation == generation) logout("Não foi possível acessar esta conta. Entre novamente.") }
        catch (_: IOException) { if (operation == generation) state = current.copy(message = "Não foi possível verificar sua conta. Tente novamente.") }
        catch (_: Exception) { if (operation == generation) logout("Não foi possível identificar sua conta. Entre novamente.") }
    }

    fun logout(message: String? = null) { generation++; state = SessionState.SignedOut(message) }

    fun clearExpiredRateLimit() {
        val current = state as? SessionState.SignedOut ?: return
        if (current.retryAtMillis?.let { it <= now() } == true) state = SessionState.SignedOut()
    }

    fun unauthorized() = logout("Sessão inválida ou expirada. Entre novamente.")
}
