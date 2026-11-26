package br.com.sysanimais.api.adocoes.domain

import jakarta.persistence.*
import java.time.Instant

enum class StatusSolicitacao { PENDENTE, APROVADA, REJEITADA, CANCELADA, CONCLUIDA }

@Entity @Table(name="solicitacao_adocao")
class SolicitacaoAdocao(
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) var id:Long?=null,
 @Column(nullable=false) var animalId:Long=0,
 @Column(nullable=false) var usuarioId:Int=0,
 @Column(nullable=false) var ongId:Int=0,
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) var status:StatusSolicitacao=StatusSolicitacao.PENDENTE,
 @Column(length=2000) var mensagem:String?=null,
 @Column(length=1000) var justificativaRejeicao:String?=null,
 @Column(length=1000) var justificativaCancelamento:String?=null,
 var analisadoPor:Int?=null, var analisadoEm:Instant?=null,
 var canceladoPor:Int?=null, var canceladoEm:Instant?=null,
 @Column(nullable=false) var criadoEm:Instant=Instant.now(),
 @Column(nullable=false) var atualizadoEm:Instant=Instant.now(),
 @Version @Column(nullable=false) var versao:Long=0
)
@Entity @Table(name="adocao")
class Adocao(
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) var id:Long?=null,
 @Column(nullable=false,unique=true) var solicitacaoId:Long=0,
 @Column(nullable=false,unique=true) var animalId:Long=0,
 @Column(nullable=false) var usuarioId:Int=0,
 @Column(nullable=false) var ongId:Int=0,
 @Column(nullable=false) var confirmadoPor:Int=0,
 @Column(nullable=false) var dataEntrega:Instant=Instant.now()
)
@Entity @Table(name="historico_adocao")
class HistoricoAdocao(
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) var id:Long?=null,
 @Column(nullable=false) var solicitacaoId:Long=0,
 @Column(nullable=false) var atorId:Int=0,
 @Column(nullable=false,length=40) var operacao:String="",
 @Column(length=16) var anterior:String?=null,
 @Column(nullable=false,length=16) var posterior:String="",
 @Column(length=1000) var justificativa:String?=null,
 @Column(nullable=false) var ocorridoEm:Instant=Instant.now()
)
