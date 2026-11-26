package br.com.sysanimais.api

import br.com.sysanimais.api.usuarios.domain.Perfil
import br.com.sysanimais.api.usuarios.domain.PerfilConverter
import br.com.sysanimais.api.usuarios.domain.Usuario
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PerfilDominioTests {
    @Test fun `perfil global em coluna textual possui conversao explicita`() {
        val converter = PerfilConverter()
        assertEquals("admin_plataforma", converter.convertToDatabaseColumn(Perfil.ADMIN_PLATAFORMA))
        assertEquals(Perfil.ADMIN_PLATAFORMA, converter.convertToEntityAttribute("admin_plataforma"))
        assertThrows(IllegalArgumentException::class.java) { converter.convertToEntityAttribute("perfil_inexistente") }
    }

    @Test fun `vinculos sao validados antes de persistir`() {
        Usuario(perfil=Perfil.ADMIN_PLATAFORMA, ongId=null).validarVinculo()
        Usuario(perfil=Perfil.USUARIO, ongId=null).validarVinculo()
        assertThrows(IllegalArgumentException::class.java) { Usuario(perfil=Perfil.ADMIN_PLATAFORMA, ongId=1).validarVinculo() }
        for (perfil in listOf(Perfil.ADMINISTRADOR, Perfil.VOLUNTARIO, Perfil.VETERINARIO)) {
            assertThrows(IllegalArgumentException::class.java) { Usuario(perfil=perfil, ongId=null).validarVinculo() }
            Usuario(perfil=perfil, ongId=1).validarVinculo()
        }
    }
}
