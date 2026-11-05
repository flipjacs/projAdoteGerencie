package br.com.patasunidas.data

import androidx.annotation.DrawableRes
import br.com.patasunidas.R

enum class Species(val label: String) { DOG("Cão"), CAT("Gato") }

data class AnimalHealth(
    val summary: String,
    val vaccines: String,
    val neutering: String,
    val deworming: String,
    val exams: String
)

data class DemoAnimal(
    val id: String,
    val name: String,
    val species: Species,
    val sex: String,
    val size: String,
    val age: String,
    val description: String,
    val status: String,
    val health: AnimalHealth,
    @DrawableRes val photo: Int
)

/** Dados e fotografias ilustrativos do showcase; não representam prontuários ou animais reais da ONG. */
object DemoAnimals {
    val all = listOf(
        DemoAnimal(
            "thor", "Thor", Species.DOG, "Macho", "Médio", "3 anos",
            "Resgatado de uma obra abandonada. Brincalhão, anda bem na guia e convive com outros cães. Precisa de passeios diários.",
            "Disponível para adoção",
            AnimalHealth("Vacinado e castrado", "Vacinas em dia", "Castrado", "Em dia", "Sem restrições informadas"),
            R.drawable.animal_thor
        ),
        DemoAnimal(
            "mel", "Mel", Species.DOG, "Fêmea", "Pequeno", "4 anos",
            "Sociável e tranquila, Mel gosta de companhia e de passeios curtos. Espera uma família com tempo para brincar.",
            "Disponível para adoção",
            AnimalHealth("Vacinada, castração agendada", "Vacinação em dia", "Agendada", "Em dia", "Avaliação inicial sem apontamentos"),
            R.drawable.animal_mel
        ),
        DemoAnimal(
            "nina", "Nina", Species.CAT, "Fêmea", "Pequeno", "1 ano",
            "Curiosa e carinhosa, Nina adora observar a casa e descansar perto de pessoas. Está se acostumando com outros gatos.",
            "Disponível para adoção",
            AnimalHealth("Vacinada e castrada", "Vacinas em dia", "Castrada", "Em dia", "Sem restrições informadas"),
            R.drawable.animal_nina
        ),
        DemoAnimal(
            "simba", "Simba", Species.CAT, "Macho", "Médio", "2 anos",
            "Simba é observador e gosta de brincar com bolinhas. Prefere um ambiente calmo para explorar no próprio ritmo.",
            "Disponível para adoção",
            AnimalHealth("Vacinado, exames em acompanhamento", "Vacinas em dia", "Castrado", "Em dia", "Avaliação de rotina agendada"),
            R.drawable.animal_simba
        ),
        DemoAnimal(
            "bolinha", "Bolinha", Species.DOG, "Macho", "Pequeno", "6 anos",
            "Bolinha é companheiro e aprecia caminhadas tranquilas. Procura um lar acolhedor para sua rotina mais calma.",
            "Disponível para adoção",
            AnimalHealth("Vacinado e castrado", "Vacinas em dia", "Castrado", "Em dia", "Acompanhamento de rotina"),
            R.drawable.animal_bolinha
        )
    )

    fun byId(id: String?) = all.firstOrNull { it.id == id }
}
