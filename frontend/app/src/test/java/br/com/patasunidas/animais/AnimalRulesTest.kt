package br.com.patasunidas.animais

import br.com.patasunidas.data.UserProfile
import org.junit.Assert.*
import org.junit.Test

class AnimalRulesTest {
 @Test fun `perfis e transicoes respeitam a API`() {
  assertTrue(canManageAnimals(UserProfile.ADMINISTRADOR)); assertTrue(canManageAnimals(UserProfile.VOLUNTARIO))
  assertFalse(canManageAnimals(UserProfile.VETERINARIO)); assertFalse(canManageAnimals(UserProfile.USUARIO))
  assertEquals(listOf(AnimalStatus.DISPONIVEL,AnimalStatus.FALECIDO), AnimalStatus.EM_TRATAMENTO.next())
  assertFalse(AnimalStatus.DISPONIVEL.next().contains(AnimalStatus.ADOTADO))
  assertTrue(AnimalStatus.FALECIDO.next().isEmpty())
 }
 @Test fun `valida datas e valores obrigatorios`() {
  val form=AnimalForm(nome="Luna",especie="CAO",sexo="FEMEA",porte="MEDIO",dataEntrada="2025-01-01")
  assertNull(form.validate()); assertNotNull(form.copy(nome=" ").validate())
  assertNotNull(form.copy(especie="INVALIDA").validate()); assertNotNull(form.copy(dataResgate="2025-02-01").validate())
  assertNotNull(form.copy(dataEntrada="2025-02-30").validate())
 }
}
