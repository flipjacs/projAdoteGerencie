package br.com.sysanimais.api.financeiro.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

enum class StatusDoacao { PENDENTE, CONFIRMADA, CANCELADA }
enum class TipoMovimento { RECEITA, DESPESA }
enum class StatusMovimento { PENDENTE, APROVADA, REALIZADA, CANCELADA }
enum class CategoriaFinanceira { DOACAO, APADRINHAMENTO, EVENTO, OUTRA_RECEITA, ALIMENTACAO, MEDICAMENTOS, VETERINARIO, TRANSPORTE, MANUTENCAO, OUTRA_DESPESA;
 fun manualPara(tipo:TipoMovimento)=if(tipo==TipoMovimento.RECEITA) this in setOf(EVENTO,OUTRA_RECEITA) else this in setOf(ALIMENTACAO,MEDICAMENTOS,VETERINARIO,TRANSPORTE,MANUTENCAO,OUTRA_DESPESA)
}
@Entity @Table(name="doacao")
class Doacao(
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) var id:Long?=null,
 @Column(nullable=false) var ongId:Int=0,
 @Column(nullable=false) var usuarioId:Int=0,
 @Column(nullable=false,precision=12,scale=2) var valor:BigDecimal=BigDecimal.ZERO,
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) var status:StatusDoacao=StatusDoacao.PENDENTE,
 @Column(length=2000) var mensagem:String?=null,
 var confirmadoPor:Int?=null, var confirmadoEm:Instant?=null,
 var canceladoPor:Int?=null, var canceladoEm:Instant?=null,
 @Column(length=1000) var justificativaCancelamento:String?=null,
 @Column(nullable=false) var criadoEm:Instant=Instant.now(),
 @Column(nullable=false) var atualizadoEm:Instant=Instant.now(),
 @Version @Column(nullable=false) var versao:Long=0
)
@Entity @Table(name="movimentacao_financeira")
class MovimentacaoFinanceira(
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) var id:Long?=null,
 @Column(nullable=false) var ongId:Int=0,
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) var tipo:TipoMovimento=TipoMovimento.RECEITA,
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) var categoria:CategoriaFinanceira=CategoriaFinanceira.OUTRA_RECEITA,
 @Column(nullable=false,length=255) var descricao:String="",
 @Column(nullable=false,precision=12,scale=2) var valor:BigDecimal=BigDecimal.ZERO,
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) var status:StatusMovimento=StatusMovimento.PENDENTE,
 @Column(unique=true) var doacaoId:Long?=null,
 @Column(nullable=false) var registradoPor:Int=0,
 var aprovadoPor:Int?=null, var aprovadoEm:Instant?=null,
 var confirmadoPor:Int?=null, var confirmadoEm:Instant?=null,
 var canceladoPor:Int?=null, var canceladoEm:Instant?=null,
 @Column(length=1000) var justificativaCancelamento:String?=null,
 @Column(nullable=false) var criadoEm:Instant=Instant.now(),
 @Column(nullable=false) var atualizadoEm:Instant=Instant.now(),
 @Version @Column(nullable=false) var versao:Long=0,
 @Column(unique=true) var parcelaApadrinhamentoId:Long?=null
)
@Entity @Table(name="historico_financeiro")
class HistoricoFinanceiro(
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) var id:Long?=null,
 var doacaoId:Long?=null, var movimentacaoId:Long?=null,
 @Column(nullable=false) var atorId:Int=0,
 @Column(nullable=false,length=40) var operacao:String="",
 @Column(length=16) var anterior:String?=null,
 @Column(nullable=false,length=16) var posterior:String="",
 @Column(length=1000) var justificativa:String?=null,
 @Column(nullable=false) var ocorridoEm:Instant=Instant.now()
)
