package br.com.patasunidas

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import br.com.patasunidas.animais.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import br.com.patasunidas.data.*
import br.com.patasunidas.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(PU.Green.toArgb()),
            navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        )
        val session = ViewModelProvider(this)[SessionViewModel::class.java]
        setContent { PatasUnidasApp(session) }
    }
}

private enum class Screen { LOGIN, CADASTRO, HOME, ANIMAIS, DETALHES }
private enum class OngScreen { HOME, ANIMALS, DETAILS, CREATE, EDIT }

@Composable
fun PatasUnidasApp(model: SessionViewModel) {
    var screen by remember { mutableStateOf(Screen.LOGIN) }
    var ongScreen by rememberSaveable { mutableStateOf(OngScreen.HOME) }
    var ongAnimalId by rememberSaveable { mutableStateOf<Long?>(null) }
    var registering by remember { mutableStateOf(false) }
    var animalFilter by remember { mutableStateOf(AnimalFilter.ALL) }
    var selectedAnimalId by remember { mutableStateOf<String?>(null) }
    val authApi = model.api
    val controller = model.controller
    val state = controller.state
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    fun logout() {
        controller.logout()
        model.clearAnimals()
        ongScreen = OngScreen.HOME
        ongAnimalId = null
        screen = Screen.LOGIN
        animalFilter = AnimalFilter.ALL
        selectedAnimalId = null
    }

    LaunchedEffect(state is SessionState.SignedOut) {
        if (state is SessionState.SignedOut) {
            model.clearAnimals()
            screen = Screen.LOGIN
            ongScreen = OngScreen.HOME
            ongAnimalId = null
            animalFilter = AnimalFilter.ALL
            selectedAnimalId = null
        }
    }

    DisposableEffect(lifecycle, controller) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) controller.expireIfNeeded()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val expiry = when (state) {
        is SessionState.Authenticated -> state.session.expiresAtMillis
        is SessionState.RetryIdentity -> state.session.expiresAtMillis
        else -> null
    }
    LaunchedEffect(expiry) {
        if (expiry != null) {
            delay((expiry - System.currentTimeMillis()).coerceAtLeast(0))
            controller.expireIfNeeded()
        }
    }
    val retryAt = (state as? SessionState.SignedOut)?.retryAtMillis
    LaunchedEffect(retryAt) {
        if (retryAt != null) {
            while (true) {
                val remaining = retryAt - System.currentTimeMillis()
                if (remaining <= 0) { controller.clearExpiredRateLimit(); break }
                delay(remaining)
            }
        }
    }
    val authenticated = state as? SessionState.Authenticated
    LaunchedEffect(authenticated?.identity?.isInternal, authenticated?.identity?.ongId) {
        if (authenticated != null && (!authenticated.identity.isInternal || !authenticated.identity.hasOng)) model.clearAnimals()
    }
    LaunchedEffect(authenticated?.message) { authenticated?.message?.let(::toast) }
    val selectedAnimal = DemoAnimals.byId(selectedAnimalId)
    when {
        authenticated != null && authenticated.identity.isInternal -> {
            val store = remember(authenticated.session, authenticated.identity.ongId) { model.animalsFor(authenticated.session, authenticated.identity.ongId) }
            val canEdit = canManageAnimals(authenticated.identity.perfil)
            LaunchedEffect(authenticated.identity.ongId, authenticated.identity.perfil) {
                if (!authenticated.identity.hasOng) ongScreen = OngScreen.HOME
                else if (!canEdit && (ongScreen == OngScreen.CREATE || ongScreen == OngScreen.EDIT)) ongScreen = OngScreen.ANIMALS
            }
            LaunchedEffect(ongScreen, ongAnimalId, store) {
                if ((ongScreen == OngScreen.DETAILS || ongScreen == OngScreen.EDIT) && store.selected == null && ongAnimalId != null && authenticated.identity.hasOng) store.details(ongAnimalId!!)
            }
            if (!authenticated.identity.hasOng) {
                BackHandler { }
                OngHomeScreen(authenticated.identity, onLogout = ::logout, onAnimals = {})
            } else when (ongScreen) {
                OngScreen.HOME -> { BackHandler { }; OngHomeScreen(authenticated.identity, onLogout = ::logout, onAnimals = { ongScreen = OngScreen.ANIMALS }) }
                OngScreen.ANIMALS -> { BackHandler { ongScreen = OngScreen.HOME }; OngAnimalsScreen(store, canEdit, onBack = { ongScreen = OngScreen.HOME }, onCreate = { if(canEdit) ongScreen = OngScreen.CREATE }, onDetail = { id -> store.details(id) { if (ongScreen == OngScreen.ANIMALS) { ongAnimalId=id; ongScreen=OngScreen.DETAILS } } }) }
                OngScreen.DETAILS -> OngAnimalDetailsScreen(store, canEdit, onBack = { ongScreen=OngScreen.ANIMALS }, onEdit = { if(canEdit) ongScreen=OngScreen.EDIT })
                OngScreen.CREATE -> if(canEdit) AnimalFormScreen(store, null, onBack = { ongScreen=OngScreen.ANIMALS }, onDone = { ongAnimalId=store.selected?.id; ongScreen=OngScreen.DETAILS })
                OngScreen.EDIT -> if(canEdit) { val animal=store.selected; if(animal!=null) AnimalFormScreen(store, animal, onBack = { ongScreen=OngScreen.DETAILS }, onDone = { ongScreen=OngScreen.DETAILS }) else OngAnimalDetailsScreen(store, false, onBack = { ongScreen=OngScreen.ANIMALS }, onEdit = {}) }
            }
        }
        authenticated != null -> {
            when (screen) {
                Screen.ANIMAIS -> {
                    BackHandler { screen = Screen.HOME }
                    AnimalsScreen(filter = animalFilter, onFilterChange = { animalFilter = it },
                        onAnimalClick = { selectedAnimalId = it; screen = Screen.DETALHES },
                        onHome = { screen = Screen.HOME }, onSoon = { toast("Em breve — este é um protótipo demonstrativo") })
                }
                Screen.DETALHES -> {
                    if (selectedAnimal != null) {
                        BackHandler { screen = Screen.ANIMAIS }
                        AnimalDetailsScreen(selectedAnimal, onBack = { screen = Screen.ANIMAIS })
                    } else HomeScreen(onAnimals = { screen = Screen.ANIMAIS }, onAnimalClick = { selectedAnimalId = it; screen = Screen.DETALHES }, onSoon = { toast("Em breve — este é um protótipo demonstrativo") }, onLogout = ::logout)
                }
                else -> HomeScreen(onAnimals = { screen = Screen.ANIMAIS }, onAnimalClick = { selectedAnimalId = it; screen = Screen.DETALHES }, onSoon = { toast("Em breve — este é um protótipo demonstrativo") }, onLogout = ::logout)
            }
        }
        screen == Screen.CADASTRO && state is SessionState.SignedOut -> {
            BackHandler { if (!registering) screen = Screen.LOGIN }
            CadastroScreen(submitting = registering, onRegister = { nome, email, senha ->
                if (!registering) {
                    registering = true
                    scope.launch {
                        try {
                            authApi.register(nome, email, senha)
                            toast("Conta criada! Entre com seu e-mail e senha.")
                            screen = Screen.LOGIN
                        } catch (_: EmailAlreadyUsedException) { toast("Este e-mail já está cadastrado") }
                        catch (_: InvalidRegistrationException) { toast("Dados inválidos. Confira os campos.") }
                        catch (_: IOException) { toast("Não foi possível conectar à API. Tente novamente.") }
                        catch (_: Exception) { toast("Falha no cadastro. Tente novamente.") }
                        finally { registering = false }
                    }
                }
            }, onBack = { if (!registering) screen = Screen.LOGIN }, onMessage = ::toast)
        }
        else -> {
            val retry = state as? SessionState.RetryIdentity
            LoginScreen(onRegister = { if (state is SessionState.SignedOut) screen = Screen.CADASTRO },
                onLogin = { email, senha, _ ->
                    if (email.isBlank() || senha.isBlank()) toast("Informe e-mail e senha")
                    else { screen = Screen.LOGIN; model.scope.launch { controller.login(email, senha) } }
                },
                onSoon = { toast("Em breve — este é um protótipo demonstrativo") },
                busy = state is SessionState.Loading,
                loginBlocked = retryAt?.let { it > System.currentTimeMillis() } == true,
                message = when (state) { is SessionState.SignedOut -> state.message; is SessionState.RetryIdentity -> state.message; else -> null },
                retryIdentity = retry != null,
                onRetryIdentity = { model.scope.launch { controller.retryIdentity() } },
                onCancelIdentity = ::logout)
        }
    }
}
