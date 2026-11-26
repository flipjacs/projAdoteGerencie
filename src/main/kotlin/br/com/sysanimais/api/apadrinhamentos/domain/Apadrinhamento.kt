package br.com.sysanimais.api.apadrinhamentos.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

enum class ModalidadeApadrinhamento { UNICO, MENSAL }
enum class StatusApadrinhamento { ATIVO, ENCERRADO }
enum class StatusParcela { PENDENTE, CONFIRMADA, CANCELADA }
enum class MotivoEncerramento { CANCELAMENTO_USUARIO, ENCERRAMENTO_ONG, ADOCAO_CONCLUIDA, FALECIMENTO_ANIMAL }
@Entity @Table(name="apadrinhamento")
class Apadrinhamento(
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) var id:Long?=null,
 @Column(nullable=false) var animalId:Long=0,
 @Column(nullable=false) var ongId:Int=0,
 @Column(nullable=false) var usuarioId:Int=0,
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=10) var modalidade:ModalidadeApadrinhamento=ModalidadeApadrinhamento.MENSAL,
 @Column(nullable=false,precision=12,scale=2) var valor:BigDecimal=BigDecimal.ZERO,
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=12) var status:StatusApadrinhamento=StatusApadrinhamento.ATIVO,
 @Column(nullable=false) var dataInicio:LocalDate=LocalDate.now(java.time.ZoneOffset.UTC),
 var dataEncerramento:LocalDate?=null,
 @Enumerated(EnumType.STRING) @Column(length=40) var motivoEncerramento:MotivoEncerramento?=null,
 @Column(length=1000) var justificativaEncerramento:String?=null,
 @Column(nullable=false) var criadoEm:Instant=Instant.now(),
 @Column(nullable=false) var atualizadoEm:Instant=Instant.now(),
 @Version @Column(nullable=false) var versao:Long=0
)
@Entity @Table(name="parcela_apadrinhamento")
class ParcelaApadrinhamento(
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) var id:Long?=null,
 @Column(nullable=false) var apadrinhamentoId:Long=0,
 @Column(nullable=false) var ongId:Int=0,
 @Column(nullable=false) var numero:Int=1,
 @Column(nullable=false,precision=12,scale=2) var valor:BigDecimal=BigDecimal.ZERO,
 @Column(nullable=false) var vencimento:LocalDate=LocalDate.now(java.time.ZoneOffset.UTC),
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=12) var status:StatusParcela=StatusParcela.PENDENTE,
 var confirmadoEm:Instant?=null,var confirmadoPor:Int?=null,
 @Column(nullable=false) var criadoEm:Instant=Instant.now(),
 @Column(nullable=false) var atualizadoEm:Instant=Instant.now(),
 @Version @Column(nullable=false) var versao:Long=0
)
