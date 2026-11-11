package br.com.patasunidas.data

import androidx.lifecycle.ViewModel
import br.com.patasunidas.BuildConfig
import br.com.patasunidas.animais.AnimalApi
import br.com.patasunidas.animais.AnimalStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class SessionViewModel : ViewModel() {
    val api = AuthApi(BuildConfig.API_BASE_URL)
    val controller = SessionController(api)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var animalSession: AuthSession? = null
    private var animalStore: AnimalStore? = null
    private var animalOngId: Int? = null
    fun animalsFor(session: AuthSession, ongId: Int?): AnimalStore {
        if (animalSession !== session || animalOngId != ongId) { clearAnimals(); animalSession = session; animalOngId = ongId; animalStore = AnimalStore(AnimalApi(BuildConfig.API_BASE_URL), controller, session, ongId) }
        return requireNotNull(animalStore)
    }
    fun clearAnimals() { animalStore?.close(); animalStore = null; animalSession = null; animalOngId = null }
    override fun onCleared() { clearAnimals(); scope.cancel() }
}
