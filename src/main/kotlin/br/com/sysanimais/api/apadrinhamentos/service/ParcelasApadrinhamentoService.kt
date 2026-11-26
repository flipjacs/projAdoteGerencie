package br.com.sysanimais.api.apadrinhamentos.service

import br.com.sysanimais.api.apadrinhamentos.domain.*
import br.com.sysanimais.api.apadrinhamentos.repository.*
import br.com.sysanimais.api.animais.domain.Animal
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.*
import org.springframework.http.HttpStatus
import java.time.*

class ApadrinhamentoException(val status:HttpStatus,val code:String,message:String):RuntimeException(message)
internal fun apadBad(message:String="Confira os campos e filtros do apadrinhamento"):Nothing=throw ApadrinhamentoException(HttpStatus.BAD_REQUEST,"APADRINHAMENTO_DADOS_INVALIDOS",message)
internal fun apadDenied():Nothing=throw ApadrinhamentoException(HttpStatus.FORBIDDEN,"APADRINHAMENTO_ACESSO_NEGADO","Operação não autorizada")
internal fun apadMissing():Nothing=throw ApadrinhamentoException(HttpStatus.NOT_FOUND,"APADRINHAMENTO_NAO_ENCONTRADO","Recurso não encontrado para esta conta ou ONG")
internal fun apadConflict(message:String="Apadrinhamento ou parcela incompatível com esta operação"):Nothing=throw ApadrinhamentoException(HttpStatus.CONFLICT,"APADRINHAMENTO_CONFLITO",message)

@Service
class ParcelasApadrinhamentoService(private val parcelas:ParcelaApadrinhamentoRepository) {
 /** Caller owns the transaction and the animal/sponsorship locks. No scheduler or unbounded futures. */
 @Transactional(propagation=Propagation.MANDATORY)
 fun ensureDue(a:Apadrinhamento,referenceDay:LocalDate=LocalDate.now(ZoneOffset.UTC),cutoff:Boolean=false) {
  if(a.modalidade!=ModalidadeApadrinhamento.MENSAL||a.status!=StatusApadrinhamento.ATIVO)return
  val start=a.dataInicio;val months=(referenceDay.year-start.year)*12+referenceDay.monthValue-start.monthValue
  if(months<0)return
  val target=months+1-if(cutoff&&start.plusMonths(months.toLong()).isAfter(referenceDay))1 else 0
  if(target>240)apadConflict("Período de parcelas excede o limite de 20 anos")
  val last=(parcelas.lockExistentes(a.id!!).firstOrNull()?.numero?:0);if(last>=target)return
  val now=Instant.now()
  parcelas.saveAllAndFlush((last+1..target).map { number -> ParcelaApadrinhamento(apadrinhamentoId=a.id!!,ongId=a.ongId,numero=number,valor=a.valor,vencimento=start.plusMonths(number.toLong()-1),criadoEm=now,atualizadoEm=now) })
 }
}
@Service
class EncerramentoApadrinhamentoService(private val sponsors:ApadrinhamentoRepository,private val generator:ParcelasApadrinhamentoService) {
 /** Called only after the existing animal transition, inside its transaction and lock. */
 @Transactional(propagation=Propagation.MANDATORY)
 fun encerrarAtivos(animal:Animal,motivo:MotivoEncerramento,eventAt:Instant) {
  val day=eventAt.atZone(ZoneOffset.UTC).toLocalDate()
  for(a in sponsors.lockAtivos(animal.id!!)) {
   generator.ensureDue(a,day,true)
   a.status=StatusApadrinhamento.ENCERRADO;a.dataEncerramento=day;a.motivoEncerramento=motivo;a.atualizadoEm=eventAt
  }
  sponsors.flush()
 }
}
