package br.com.patasunidas.apadrinhamentos

import br.com.patasunidas.data.UserProfile
import java.text.SimpleDateFormat
import java.util.Locale

enum class SponsorMode(val label:String){UNICO("Único"),MENSAL("Mensal")}
enum class SponsorStatus(val label:String){ATIVO("Ativo"),ENCERRADO("Encerrado")}
enum class InstallmentStatus(val label:String){PENDENTE("Pendente"),CONFIRMADA("Confirmada"),CANCELADA("Cancelada")}
enum class SponsorClosureReason(val label:String){CANCELAMENTO_USUARIO("Cancelamento pelo usuário"),ENCERRAMENTO_ONG("Encerramento pela ONG"),ADOCAO_CONCLUIDA("Adoção concluída"),FALECIMENTO_ANIMAL("Falecimento do animal")}
enum class DemonstrativeStatus(val label:String){RASCUNHO("Rascunho"),PUBLICADO("Publicado")}
fun canManageSupport(profile:UserProfile)=profile in listOf(UserProfile.ADMINISTRADOR,UserProfile.VOLUNTARIO)
internal fun validSupportDate(value:String):Boolean {if(!Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(value)||value.take(4).toInt() !in 1000..9999)return false;return try{SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).apply{isLenient=false}.parse(value)!=null}catch(_:Exception){false}}
internal fun validateSupportPeriod(de:String,ate:String){require((de.isEmpty()||validSupportDate(de))&&(ate.isEmpty()||validSupportDate(ate))&&(de.isEmpty()||ate.isEmpty()||ate>=de)){"Informe um período válido (AAAA-MM-DD), com fim igual ou posterior ao início."}}
data class SupportEntity(val id:Long,val name:String)
data class SupportPerson(val id:Int,val name:String,val email:String?=null)
data class SupportPage<T>(val items:List<T>,val page:Int,val size:Int,val total:Long,val pages:Int)
data class InstallmentRecord(val id:Long,val number:Int,val value:String,val dueDate:String,val status:InstallmentStatus,val overdue:Boolean,val confirmedAt:String?,val confirmedBy:SupportPerson?)
data class SponsorRecord(val id:Long,val animal:SupportEntity,val ong:SupportEntity,val mode:SponsorMode,val value:String,val status:SponsorStatus,val startDate:String,val endDate:String?,val closureReason:SponsorClosureReason?,val closureJustification:String?,val nextDueDate:String?,val created:String,val updated:String,val version:Long,val firstInstallment:InstallmentRecord?,val sponsor:SupportPerson?)
data class FinancialReport(val de:String?,val ate:String?,val periodSemantics:String,val receitasRealizadas:String,val despesasRealizadas:String,val receitasPendentes:String,val despesasPendentes:String,val despesasAprovadas:String,val saldoRealizado:String,val quantidadeMovimentacoes:Long,val receitasPorCategoria:Map<String,String>,val despesasPorCategoria:Map<String,String>)
/** Whitelisted snapshot fields only; independent of internal reports and ledger records. */
data class PublicTransparencyData(val receitasRealizadas:String,val despesasRealizadas:String,val resultadoLiquido:String,val receitasPorCategoria:Map<String,String>,val despesasPorCategoria:Map<String,String>,val quantidadeMovimentacoes:Long)
/** Exactly the eight fields of the anonymous projection, no administrative or donor data. */
data class PublicDemonstrativeRecord(val id:Long,val ong:SupportEntity,val title:String,val description:String?,val startDate:String,val endDate:String,val publishedAt:String?,val data:PublicTransparencyData)
data class DemonstrativeRecord(val id:Long,val ong:SupportEntity,val title:String,val description:String?,val startDate:String,val endDate:String,val publishedAt:String?,val data:PublicTransparencyData,val status:DemonstrativeStatus,val publishedBy:Int?,val unpublishedAt:String?,val created:String,val updated:String)
data class DemonstrativeForm(val title:String="",val description:String="",val startDate:String="",val endDate:String=""){
 fun validate():String? {val t=title.trim();val d=description.trim();if(t.isEmpty()||t.codePointCount(0,t.length)>120)return "Informe o título (até 120 caracteres).";if(d.codePointCount(0,d.length)>2000)return "Informe uma descrição com até 2.000 caracteres.";if(!validSupportDate(startDate)||!validSupportDate(endDate)||endDate<startDate)return "Informe um período válido (AAAA-MM-DD), com fim igual ou posterior ao início.";return null}
}
