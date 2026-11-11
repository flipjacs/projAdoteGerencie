package br.com.sysanimais.api.animais.domain

import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDate

enum class EspecieAnimal { CAO, GATO, OUTRO }
enum class SexoAnimal { MACHO, FEMEA, DESCONHECIDO }
enum class PorteAnimal { PEQUENO, MEDIO, GRANDE, DESCONHECIDO }
enum class SituacaoAnimal { EM_TRATAMENTO, DISPONIVEL, EM_PROCESSO_ADOCAO, ADOTADO, FALECIDO }

@Entity @Table(name="animal")
class Animal(
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) var id: Long? = null,
 @Column(name="ong_id", nullable=false) var ongId: Int = 0,
 @Column(nullable=false,length=100) var nome: String = "",
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) var especie: EspecieAnimal = EspecieAnimal.OUTRO,
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) var sexo: SexoAnimal = SexoAnimal.DESCONHECIDO,
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) var porte: PorteAnimal = PorteAnimal.DESCONHECIDO,
 var nascimentoEstimado: LocalDate? = null,
 @Column(length=2000) var descricao: String? = null,
 @Column(length=2000) var historia: String? = null,
 var dataResgate: LocalDate? = null,
 @Column(length=255) var origemResgate: String? = null,
 @Column(length=2000) var observacoesResgate: String? = null,
 @Column(nullable=false) var dataEntrada: LocalDate = LocalDate.now(),
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) var situacao: SituacaoAnimal = SituacaoAnimal.EM_TRATAMENTO,
 @Column(nullable=false) var situacaoAtualizadaEm: Instant = Instant.now(),
 @Column(nullable=false) var situacaoAlteradaPor: Int = 0,
 @Column(nullable=false) var ativo: Boolean = true,
 @Column(nullable=false) var publicado: Boolean = false,
 @Column(nullable=false) var criadoEm: Instant = Instant.now(),
 @Column(nullable=false) var atualizadoEm: Instant = Instant.now(),
 @Version @Column(nullable=false) var versao: Long = 0
)
@Entity @Table(name="animal_foto")
class AnimalFoto(
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) var id: Long? = null,
 @Column(name="animal_id",nullable=false) var animalId: Long = 0,
 @Column(nullable=false,unique=true,length=80) var arquivo: String = "",
 @Column(nullable=false,length=20) var tipo: String = "",
 @Column(nullable=false) var principal: Boolean = false,
 @Column(nullable=false) var criadoEm: Instant = Instant.now()
)
