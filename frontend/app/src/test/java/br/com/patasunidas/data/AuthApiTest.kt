package br.com.patasunidas.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AuthApiTest {
    @Test fun cadastroLoginEIdentidadeUsamContrato() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(MockResponse().setResponseCode(201))
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"accessToken":"teste","tokenType":"Bearer","expiresIn":3600}"""))
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":1,"nome":"Teste","email":"user@example.test","perfil":"ADMIN_PLATAFORMA","ongId":null}"""))
            val api = AuthApi(server.url("/").toString())
            api.register("Teste", "user@example.test", "senhaSegura123")
            val session = api.login("user@example.test", "senhaSegura123")
            val identity = api.me(session.accessToken)
            assertEquals(UserProfile.ADMIN_PLATAFORMA, identity.perfil)
            assertNull(identity.ongId)
            assertEquals("/api/usuarios/cadastro", server.takeRequest().path)
            assertEquals("/api/auth/login", server.takeRequest().path)
            val me = server.takeRequest()
            assertEquals("/api/auth/me", me.path)
            assertEquals("Bearer teste", me.getHeader("Authorization"))
        } finally { server.shutdown() }
    }

    @Test fun cadastroDuplicadoELoginInvalidoSaoDistinguidos() = runBlocking {
        val server = MockWebServer()
        try {
            server.start(); server.enqueue(MockResponse().setResponseCode(409)); server.enqueue(MockResponse().setResponseCode(401))
            val api = AuthApi(server.url("/").toString())
            try { api.register("Teste", "user@example.test", "senhaSegura123"); fail() } catch (_: EmailAlreadyUsedException) { }
            try { api.login("user@example.test", "senhaSegura123"); fail() } catch (_: InvalidCredentialsException) { }
        } finally { server.shutdown() }
    }
    @Test fun primeiroAcessoAceita204EEnviaContrato() = runBlocking {
        val server = MockWebServer()
        try {
            server.start(); server.enqueue(MockResponse().setResponseCode(204))
            AuthApi(server.url("/").toString()).firstAccess("user@example.test", "temporaria", "novaSenhaSegura")
            val request = server.takeRequest()
            assertEquals("/api/auth/primeiro-acesso/trocar-senha", request.path)
            val json = JSONObject(request.body.readUtf8())
            assertEquals(setOf("email", "senhaTemporaria", "novaSenha"), json.keys().asSequence().toSet())
            assertEquals("temporaria", json.getString("senhaTemporaria"))
        } finally { server.shutdown() }
    }
    @Test fun primeiroAcessoRejeitaCredencialInvalida() = runBlocking {
        val server = MockWebServer()
        try {
            server.start(); server.enqueue(MockResponse().setResponseCode(401))
            try { AuthApi(server.url("/").toString()).firstAccess("x", "x", "x"); fail("Deveria rejeitar") }
            catch (_: InvalidTemporaryCredentialException) { }
        } finally { server.shutdown() }
    }
    @Test fun reconheceCincoPerfisReais() {
        listOf("ADMIN_PLATAFORMA", "ADMINISTRADOR", "VOLUNTARIO", "VETERINARIO", "USUARIO").forEach { assertNotNull(UserProfile.fromApi(it)) }
        assertNull(UserProfile.fromApi("CRIADOR_PLATAFORMA"))
    }
}
