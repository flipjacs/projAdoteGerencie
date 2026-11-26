package br.com.patasunidas.animais

import br.com.patasunidas.data.UserProfile
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

fun canManageAnimals(profile: UserProfile) = profile == UserProfile.ADMINISTRADOR || profile == UserProfile.VOLUNTARIO

enum class AnimalStatus(val label: String) {
 EM_TRATAMENTO("Em tratamento"), DISPONIVEL("Disponível"), EM_PROCESSO_ADOCAO("Em processo de adoção"), ADOTADO("Adotado"), FALECIDO("Falecido");
 fun next(): List<AnimalStatus> = when(this) {
  EM_TRATAMENTO -> listOf(DISPONIVEL, FALECIDO)
  DISPONIVEL -> listOf(EM_TRATAMENTO, EM_PROCESSO_ADOCAO, FALECIDO)
  EM_PROCESSO_ADOCAO -> listOf(DISPONIVEL, EM_TRATAMENTO, FALECIDO)
  else -> emptyList()
 }
}
val especies = mapOf("CAO" to "Cão", "GATO" to "Gato", "OUTRO" to "Outro")
val sexos = mapOf("MACHO" to "Macho", "FEMEA" to "Fêmea", "DESCONHECIDO" to "Desconhecido")
val portes = mapOf("PEQUENO" to "Pequeno", "MEDIO" to "Médio", "GRANDE" to "Grande", "DESCONHECIDO" to "Desconhecido")

data class AnimalForm(val nome:String="",val especie:String="CAO",val sexo:String="DESCONHECIDO",val porte:String="DESCONHECIDO",val dataEntrada:String="",val nascimentoEstimado:String="",val descricao:String="",val historia:String="",val dataResgate:String="",val origemResgate:String="",val observacoesResgate:String="") {
 fun validate(): String? {
  if(nome.isBlank() || nome.length>100) return "Informe o nome (até 100 caracteres)."
  if(especie !in especies || sexo !in sexos || porte !in portes) return "Selecione espécie, sexo e porte válidos."
  if(descricao.length>2000 || historia.length>2000 || observacoesResgate.length>2000 || origemResgate.length>255) return "Um dos textos excede o limite permitido."
  val format=SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).apply { isLenient=false; timeZone=TimeZone.getTimeZone("UTC") }
  fun date(value:String): Long? = if(!value.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) null else try { format.parse(value)?.time } catch (_:Exception) { null }
  val entry=date(dataEntrada) ?: return "Informe uma data de entrada válida (AAAA-MM-DD)."
  val today=format.format(java.util.Date()); if(dataEntrada>today) return "A data de entrada não pode ser futura."
  for(value in listOf(nascimentoEstimado,dataResgate)) if(value.isNotEmpty()) { val other=date(value) ?: return "Informe datas válidas (AAAA-MM-DD)."; if(other>entry) return "Nascimento e resgate não podem ser posteriores à entrada." }
  return null
 }
}
data class AnimalPhoto(val id:Long,val principal:Boolean,val url:String)
data class Animal(val id:Long,val ongId:Int,val form:AnimalForm,val situacao:AnimalStatus,val ativo:Boolean,val fotos:List<AnimalPhoto>,val versao:Long,val publicado:Boolean=false)
data class AnimalPage(val items:List<Animal>,val number:Int,val size:Int,val total:Long,val pages:Int)

fun formDifferences(local: AnimalForm, remote: AnimalForm): List<Triple<String,String,String>> = listOf(
 Triple("Nome",local.nome,remote.nome), Triple("Espécie",especies[local.especie] ?: local.especie,especies[remote.especie] ?: remote.especie),
 Triple("Sexo",sexos[local.sexo] ?: local.sexo,sexos[remote.sexo] ?: remote.sexo), Triple("Porte",portes[local.porte] ?: local.porte,portes[remote.porte] ?: remote.porte),
 Triple("Data de entrada",local.dataEntrada,remote.dataEntrada), Triple("Nascimento estimado",local.nascimentoEstimado,remote.nascimentoEstimado),
 Triple("Descrição",local.descricao,remote.descricao), Triple("História",local.historia,remote.historia), Triple("Data do resgate",local.dataResgate,remote.dataResgate),
 Triple("Origem do resgate",local.origemResgate,remote.origemResgate), Triple("Observações",local.observacoesResgate,remote.observacoesResgate)
).filter { it.second != it.third }

data class PublicAnimal(val id: Long, val nome: String, val especie: String, val sexo: String, val porte: String, val descricao: String?, val fotos: List<AnimalPhoto>, val nascimentoEstimado: String? = null, val ongNome: String? = null, val cidade: String? = null, val estado: String? = null, val ongId: Int? = null)
data class PublicAnimalPage(val items: List<PublicAnimal>, val page: Int, val pages: Int, val total: Long)
