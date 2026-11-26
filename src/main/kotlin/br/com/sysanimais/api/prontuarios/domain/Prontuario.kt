package br.com.sysanimais.api.prontuarios.domain

import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDate

enum class TipoProntuario { CONSULTA, VACINA, VERMIFUGACAO, EXAME, CIRURGIA, TRATAMENTO, MEDICACAO, OBSERVACAO }

@Entity @Table(name="prontuario")
class Prontuario(
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) var id:Long?=null,
 @Column(nullable=false) var animalId:Long=0,
 @Column(nullable=false) var ongId:Int=0,
 @Column(nullable=false) var registradoPor:Int=0,
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) var tipo:TipoProntuario=TipoProntuario.CONSULTA,
 @Column(nullable=false,length=120) var titulo:String="",
 @Column(nullable=false,columnDefinition="TEXT") var descricao:String="",
 @Column(nullable=false) var dataProcedimento:LocalDate=LocalDate.now(),
 @Column(length=120) var profissionalResponsavel:String?=null,
 @Column(columnDefinition="TEXT") var medicamentos:String?=null,
 @Column(columnDefinition="TEXT") var observacoes:String?=null,
 @Column(nullable=false) var criadoEm:Instant=Instant.now(),
 @Column(nullable=false) var atualizadoEm:Instant=Instant.now(),
 @Version @Column(nullable=false) var versao:Long=0
)

@Entity @Table(name="correcao_prontuario")
class CorrecaoProntuario(
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) var id:Long?=null,
 @Column(nullable=false) var prontuarioId:Long=0,
 @Column(nullable=false) var corrigidoPor:Int=0,
 @Column(nullable=false,length=2000) var justificativa:String="",
 @Column(nullable=false,columnDefinition="MEDIUMTEXT") var dadosAnteriores:String="",
 @Column(nullable=false,columnDefinition="MEDIUMTEXT") var dadosNovos:String="",
 @Column(nullable=false) var versaoAnterior:Long=0,
 @Column(nullable=false) var versaoPosterior:Long=1,
 @Column(nullable=false) var criadoEm:Instant=Instant.now()
)
