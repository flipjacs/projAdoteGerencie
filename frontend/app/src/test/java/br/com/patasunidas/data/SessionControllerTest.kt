package br.com.patasunidas.data

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class SessionControllerTest {
    private class FakeApi : AuthGateway {
        var logins = 0
        var identityCalls = 0
        var identity = UserIdentity(1, "Teste", "teste@example.test", UserProfile.USUARIO, null)
        var failure: Exception? = null
        var loginFailure: Exception? = null
        var expiresAt = 2000L
        override suspend fun login(email: String, senha: String): AuthSession {
            logins++
            loginFailure?.let { throw it }
            return AuthSession("token", expiresAt)
        }
        override suspend fun me(token: String): UserIdentity {
            identityCalls++
            failure?.let { throw it }
            return identity
        }
    }

    @Test fun perfisELogout() = runBlocking {
        for (profile in UserProfile.entries) {
            val api = FakeApi().apply { identity = identity.copy(perfil = profile, ongId = if (profile == UserProfile.USUARIO) null else 1) }
            val controller = SessionController(api) { 1000 }
            controller.login("a", "b")
            val state = controller.state as SessionState.Authenticated
            assertEquals(profile != UserProfile.USUARIO, state.identity.isInternal)
            controller.logout()
            assertTrue(controller.state is SessionState.SignedOut)
        }
    }

    @Test fun redePermiteRepetirIdentidadeSemNovoLogin() = runBlocking {
        val api = FakeApi().apply { failure = IOException() }
        val controller = SessionController(api) { 1000 }
        controller.login("a", "b")
        assertTrue(controller.state is SessionState.RetryIdentity)
        api.failure = null
        controller.retryIdentity()
        assertTrue(controller.state is SessionState.Authenticated)
        assertEquals(1, api.logins)
        assertEquals(2, api.identityCalls)
    }

    @Test fun sessaoExpiradaERevogadaSaoRemovidas() = runBlocking {
        var now = 1000L
        val api = FakeApi()
        val controller = SessionController(api) { now }
        controller.login("a", "b")
        now = 2000
        controller.expireIfNeeded()
        assertTrue(controller.state is SessionState.SignedOut)
        now = 1000
        api.failure = SessionUnauthorizedException()
        controller.login("a", "b")
        assertTrue(controller.state is SessionState.SignedOut)
        api.failure = SessionForbiddenException()
        controller.login("a", "b")
        assertTrue(controller.state is SessionState.SignedOut)
    }

    @Test fun perfilDesconhecidoNaoConcedeAcesso() {
        assertNull(UserProfile.fromApi("OUTRO"))
    }

    @Test fun proibicaoComContaAtivaPreservaSessaoMasRevogacaoEncerra() = runBlocking {
        val api = FakeApi()
        val controller = SessionController(api) { 1000 }
        controller.login("a", "b")
        controller.operationForbidden()
        assertTrue(controller.state is SessionState.Authenticated)
        api.failure = SessionForbiddenException()
        controller.operationForbidden()
        assertTrue(controller.state is SessionState.SignedOut)
    }

    @Test fun revalidacaoAtualizaPerfilSemNovoLogin() = runBlocking {
        val api = FakeApi()
        val controller = SessionController(api) { 1000 }
        controller.login("a", "b")
        api.identity = api.identity.copy(perfil = UserProfile.VETERINARIO, ongId = 1)
        controller.operationForbidden()
        val state = controller.state as SessionState.Authenticated
        assertTrue(state.identity.isInternal)
        assertEquals(UserProfile.VETERINARIO, state.identity.perfil)
        assertEquals(1, api.logins)
    }

    @Test fun resposta401RemoveSessaoAtiva() = runBlocking {
        val controller = SessionController(FakeApi()) { 1000 }
        controller.login("a", "b")
        controller.unauthorized()
        assertTrue(controller.state is SessionState.SignedOut)
    }

    @Test fun logoutDuranteConsultaNaoRestauraSessao() = runBlocking {
        val pending = CompletableDeferred<UserIdentity>()
        val api = object : AuthGateway {
            override suspend fun login(email: String, senha: String) = AuthSession("token", 2000)
            override suspend fun me(token: String): UserIdentity = pending.await()
        }
        val controller = SessionController(api) { 1000 }
        val request = launch { controller.login("a", "b") }
        yield()
        controller.logout()
        pending.complete(UserIdentity(1, "Teste", "teste@example.test", UserProfile.USUARIO, null))
        request.join()
        assertTrue(controller.state is SessionState.SignedOut)
    }

    @Test fun internoSemOngNaoPossuiVinculo() {
        val user = UserIdentity(1, "Teste", "teste@example.test", UserProfile.VOLUNTARIO, null)
        assertTrue(user.isInternal)
        assertFalse(user.hasOng)
    }

    @Test fun retryAfterAceitaSegundosELimitaValores() {
        assertEquals(45L, parseRetryAfterSeconds("45"))
        assertEquals(30L, parseRetryAfterSeconds(null))
        assertEquals(30L, parseRetryAfterSeconds("inválido"))
        assertEquals(30L, parseRetryAfterSeconds("-1"))
        assertEquals(900L, parseRetryAfterSeconds("999999"))
    }

    @Test fun limiteImpedeReenvioEAposPrazoPermiteNovaTentativa() = runBlocking {
        var now = 1000L
        val api = FakeApi().apply { loginFailure = LoginRateLimitedException(45); expiresAt = 100_000 }
        val controller = SessionController(api) { now }
        controller.login("a", "b")
        val limited = controller.state as SessionState.SignedOut
        assertEquals(46000L, limited.retryAtMillis)
        assertTrue(limited.message!!.contains("45"))
        controller.login("a", "b")
        assertEquals(1, api.logins)
        now = 46000L
        controller.clearExpiredRateLimit()
        api.loginFailure = null
        controller.login("a", "b")
        assertEquals(2, api.logins)
        assertTrue(controller.state is SessionState.Authenticated)
    }

    @Test fun credenciaisInvalidasERedeMantemMensagensDistintas() = runBlocking {
        val api = FakeApi().apply { loginFailure = InvalidCredentialsException() }
        val controller = SessionController(api) { 1000 }
        controller.login("a", "b")
        assertTrue((controller.state as SessionState.SignedOut).message!!.contains("senha inválidos"))
        api.loginFailure = IOException()
        controller.login("a", "b")
        assertTrue((controller.state as SessionState.SignedOut).message!!.contains("conectar"))
        api.loginFailure = null
        controller.login("a", "b")
        assertTrue(controller.state is SessionState.Authenticated)
    }

    @Test fun tentativaDeNovoLoginNaoDescartaSessaoAtiva() = runBlocking {
        val api = FakeApi()
        val controller = SessionController(api) { 1000 }
        controller.login("a", "b")
        api.loginFailure = LoginRateLimitedException(30)
        controller.login("outro", "b")
        assertTrue(controller.state is SessionState.Authenticated)
        assertEquals(1, api.logins)
    }
}
