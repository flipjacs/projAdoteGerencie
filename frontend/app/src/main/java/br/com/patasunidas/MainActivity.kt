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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.rememberSaveable
import br.com.patasunidas.animais.*
import br.com.patasunidas.adocoes.*
import br.com.patasunidas.financeiro.*
import br.com.patasunidas.apadrinhamentos.*
import br.com.patasunidas.prontuarios.*
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
            statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        )
        val session = ViewModelProvider(this)[SessionViewModel::class.java]
        setContent { PatasUnidasTheme { PatasUnidasApp(session) } }
    }
}

private enum class Screen { LOGIN, CADASTRO, PRIMEIRO_ACESSO, HOME, ANIMAIS, DETALHES, REQUEST, MY_ADOPTIONS, DONATE, MY_DONATIONS, MEDICAL_PERSONAL, SPONSOR, MY_SUPPORTS }
private enum class OngScreen { HOME, ANIMALS, DETAILS, CREATE, EDIT, EMPLOYEES, ADOPTIONS, FINANCE, MEDICAL, SUPPORTS, REPORT, TRANSPARENCY }

@Composable
fun PatasUnidasApp(model: SessionViewModel) {
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var ongScreen by rememberSaveable { mutableStateOf(OngScreen.HOME) }
    var ongAnimalId by rememberSaveable { mutableStateOf<Long?>(null) }
    var returnAnimal by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingAdoption by rememberSaveable { mutableStateOf<Long?>(null) }
    var donationOng by rememberSaveable { mutableStateOf<Int?>(null) }
    var donationName by rememberSaveable { mutableStateOf("") }
    var donationAnimal by rememberSaveable { mutableStateOf<Long?>(null) }
    var medicalAnimalId by rememberSaveable { mutableStateOf<Long?>(null) }
    var medicalAnimalName by rememberSaveable { mutableStateOf("") }
    var medicalAnimalStatus by rememberSaveable { mutableStateOf<AnimalStatus?>(null) }
    var pendingSponsor by rememberSaveable { mutableStateOf<Long?>(null) }
    var publicBrowsing by rememberSaveable { mutableStateOf(false) }
    var publicTransparency by rememberSaveable { mutableStateOf(false) }
    var transparencyOng by rememberSaveable { mutableStateOf<Int?>(null) }
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
        medicalAnimalId = null
        medicalAnimalName = ""
        medicalAnimalStatus = null
        pendingAdoption = null
        pendingSponsor = null
        publicBrowsing = false
        publicTransparency = false
        transparencyOng = null
        donationOng = null
        donationAnimal = null
        returnAnimal = null
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
            medicalAnimalId = null
            medicalAnimalName = ""
            medicalAnimalStatus = null
            screen = if(state.message == null) Screen.HOME else Screen.LOGIN
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
    LaunchedEffect(authenticated?.session) {
        if (authenticated?.identity?.perfil == UserProfile.USUARIO && pendingAdoption != null) screen = Screen.REQUEST
        else if (authenticated != null) pendingAdoption = null
        if(authenticated?.identity?.perfil == UserProfile.USUARIO && donationOng != null) screen = Screen.DONATE
        else if(authenticated != null) { donationOng=null; donationAnimal=null }
    }
    LaunchedEffect(authenticated?.session) {
        if(authenticated?.identity?.perfil == UserProfile.USUARIO && pendingSponsor != null) screen=Screen.SPONSOR
        else if(authenticated != null) pendingSponsor=null
    }
    val supportApi = remember { SupportApi(BuildConfig.API_BASE_URL) }
    val supportStore = if(authenticated != null && (authenticated.identity.perfil == UserProfile.USUARIO || canManageSupport(authenticated.identity.perfil) && authenticated.identity.hasOng)) {
        remember(authenticated.session,authenticated.identity) { SupportStore(supportApi,controller,authenticated.session,authenticated.identity) }
    } else null
    DisposableEffect(supportStore) { onDispose { supportStore?.close() } }
    val supportNavigation = SupportNavigation(
        home={returnAnimal=null;screen=Screen.HOME}, adoptions={screen=Screen.MY_ADOPTIONS}, donations={screen=Screen.MY_DONATIONS}, sponsorships={screen=Screen.MY_SUPPORTS}, adopted={screen=Screen.MY_ADOPTIONS})
    fun openTransparency(ongId:Int?=null) { transparencyOng=ongId; publicTransparency=true }
    val adoptionStore = if(authenticated != null && (authenticated.identity.perfil == UserProfile.USUARIO || canManageAdoptions(authenticated.identity.perfil) && authenticated.identity.hasOng)) {
        remember(authenticated.session, authenticated.identity) { AdoptionStore(AdoptionApi(BuildConfig.API_BASE_URL), controller, authenticated.session, authenticated.identity) }
    } else null
    DisposableEffect(adoptionStore) { onDispose { adoptionStore?.close() } }
    val financeStore = if(authenticated != null && (authenticated.identity.perfil == UserProfile.USUARIO || canManageFinance(authenticated.identity.perfil) && authenticated.identity.hasOng)) {
        remember(authenticated.session,authenticated.identity) { FinanceStore(FinanceApi(BuildConfig.API_BASE_URL),controller,authenticated.session,authenticated.identity) }
    } else null
    DisposableEffect(financeStore) { onDispose { financeStore?.close() } }
    val clinicalProfiles = setOf(UserProfile.ADMINISTRADOR, UserProfile.VETERINARIO, UserProfile.VOLUNTARIO)
    val medicalActive = authenticated != null && medicalAnimalId != null &&
        ((authenticated.identity.perfil == UserProfile.USUARIO && screen == Screen.MEDICAL_PERSONAL) ||
         (authenticated.identity.perfil in clinicalProfiles && authenticated.identity.hasOng && ongScreen == OngScreen.MEDICAL))
    val medicalStore = if(medicalActive && authenticated != null) {
        remember(authenticated.session, authenticated.identity, medicalAnimalId, medicalAnimalName, medicalAnimalStatus) {
            ProntuarioStore(ProntuarioApi(BuildConfig.API_BASE_URL), controller, authenticated.session, authenticated.identity,
                medicalAnimalId!!, medicalAnimalName, medicalAnimalStatus)
        }
    } else null
    DisposableEffect(medicalStore) { onDispose { medicalStore?.close() } }
    val publicApi = remember { AnimalApi(br.com.patasunidas.BuildConfig.API_BASE_URL) }
    when {
        publicTransparency -> PublicTransparencyExperience(supportApi,transparencyOng,authenticated!=null,onBack={publicTransparency=false},onAccount={if(authenticated!=null)logout()else{publicTransparency=false;screen=Screen.LOGIN}},onHome={publicTransparency=false;publicBrowsing=authenticated?.identity?.isInternal==true;returnAnimal=null;screen=Screen.HOME},onCatalog={publicTransparency=false;publicBrowsing=authenticated?.identity?.isInternal==true;returnAnimal=null;screen=Screen.ANIMAIS})
        publicBrowsing && authenticated != null && authenticated.identity.isInternal -> {
            BackHandler { publicBrowsing=false }
            PublicAnimalsExperience(publicApi,true,onAccount=::logout,initialHome=screen!=Screen.ANIMAIS,
                onAdopt={toast("A solicitação de adoção exige uma conta de usuário.")},
                onSponsor={toast("O apadrinhamento exige uma conta de usuário.")},
                onDonate={toast("A doação pessoal exige uma conta de usuário.")},
                onTransparency={openTransparency(it)},personalActions=false,onPublicBack={publicBrowsing=false})
        }
        authenticated != null && authenticated.identity.perfil == UserProfile.ADMIN_PLATAFORMA -> {
            BackHandler { }
            InstitutionalArea(authenticated.identity, authenticated.session.accessToken, remember { InstitutionalApi(br.com.patasunidas.BuildConfig.API_BASE_URL) }, controller, onLogout = ::logout, onBack = {}, onPublicTransparency = { openTransparency() })
        }
        authenticated != null && authenticated.identity.isInternal -> {
            val store = remember(authenticated.session, authenticated.identity.ongId) { model.animalsFor(authenticated.session, authenticated.identity.ongId) }
            val canEdit = canManageAnimals(authenticated.identity.perfil)
            LaunchedEffect(authenticated.identity.ongId, authenticated.identity.perfil) {
                if (!canManageEmployees(authenticated.identity.perfil) && ongScreen == OngScreen.EMPLOYEES) ongScreen = OngScreen.HOME
                if (!canManageAdoptions(authenticated.identity.perfil) && ongScreen == OngScreen.ADOPTIONS) ongScreen = OngScreen.HOME
                if (!canManageFinance(authenticated.identity.perfil) && ongScreen == OngScreen.FINANCE) ongScreen = OngScreen.HOME
                if(!canManageSupport(authenticated.identity.perfil) && ongScreen in listOf(OngScreen.SUPPORTS,OngScreen.REPORT,OngScreen.TRANSPARENCY)) ongScreen=OngScreen.HOME
                if(authenticated.identity.perfil != UserProfile.ADMINISTRADOR && ongScreen == OngScreen.REPORT) ongScreen=OngScreen.HOME
                if (!authenticated.identity.hasOng) ongScreen = OngScreen.HOME
                else if (!canEdit && (ongScreen == OngScreen.CREATE || ongScreen == OngScreen.EDIT)) ongScreen = OngScreen.ANIMALS
            }
            LaunchedEffect(ongScreen, ongAnimalId, store) {
                if ((ongScreen == OngScreen.DETAILS || ongScreen == OngScreen.EDIT) && store.selected == null && ongAnimalId != null && authenticated.identity.hasOng) store.details(ongAnimalId!!)
            }
            InstitutionalDrawer(authenticated.identity, ongScreen, onNavigate={ ongScreen=it }) {
            if (!authenticated.identity.hasOng) {
                BackHandler { }
                OngHomeScreen(authenticated.identity, onLogout = ::logout, onAnimals = {}, onPublicTransparency={openTransparency()})
            } else when (ongScreen) {
                OngScreen.HOME -> { BackHandler { }; OngHomeScreen(authenticated.identity, onLogout = ::logout, onAnimals = { ongScreen = OngScreen.ANIMALS }, onEmployees = { ongScreen = OngScreen.EMPLOYEES }, onAdoptions = { ongScreen = OngScreen.ADOPTIONS }, onFinance = { ongScreen = OngScreen.FINANCE }, onPublicTransparency={openTransparency(authenticated.identity.ongId)}) }
                OngScreen.SUPPORTS -> if(supportStore!=null) SupportExperience(supportStore,onBack={ongScreen=OngScreen.FINANCE},onLogout=::logout,onFinancialChanged={financeStore?.loadSummary()})
                OngScreen.REPORT -> if(supportStore?.canWrite==true) SupportReportScreen(supportStore,onBack={ongScreen=OngScreen.FINANCE},onLogout=::logout)
                OngScreen.TRANSPARENCY -> if(supportStore!=null) TransparencyExperience(supportStore,onBack={ongScreen=OngScreen.FINANCE},onLogout=::logout)
                OngScreen.MEDICAL -> if(medicalStore != null) ProntuarioExperience(medicalStore,onBack={ongScreen=OngScreen.DETAILS},onLogout=::logout)
                OngScreen.FINANCE -> if(financeStore != null) FinancialExperience(financeStore,onBack={ongScreen=OngScreen.HOME},onLogout=::logout,onSupports={ongScreen=OngScreen.SUPPORTS},onReport=if(supportStore?.canWrite==true)({ongScreen=OngScreen.REPORT})else null,onTransparency={ongScreen=OngScreen.TRANSPARENCY})
                OngScreen.ADOPTIONS -> if(adoptionStore != null) AdoptionExperience(adoptionStore, onBack = { ongScreen = OngScreen.HOME; store.load() }, onLogout = ::logout)
                OngScreen.EMPLOYEES -> InstitutionalArea(authenticated.identity, authenticated.session.accessToken, remember { InstitutionalApi(br.com.patasunidas.BuildConfig.API_BASE_URL) }, controller, onLogout = ::logout, onBack = { ongScreen = OngScreen.HOME })
                OngScreen.ANIMALS -> { BackHandler { ongScreen = OngScreen.HOME }; OngAnimalsScreen(store, canEdit, onBack = { ongScreen = OngScreen.HOME }, onCreate = { if(canEdit) ongScreen = OngScreen.CREATE }, onDetail = { id -> store.details(id) { if (ongScreen == OngScreen.ANIMALS) { ongAnimalId=id; ongScreen=OngScreen.DETAILS } } }) }
                OngScreen.DETAILS -> OngAnimalDetailsScreen(store, canEdit, onBack = { ongScreen=OngScreen.ANIMALS }, onEdit = { if(canEdit) ongScreen=OngScreen.EDIT }, onProntuario = {
                    store.selected?.let { animal -> medicalAnimalId=animal.id;medicalAnimalName=animal.form.nome;medicalAnimalStatus=animal.situacao;ongScreen=OngScreen.MEDICAL }
                })
                OngScreen.CREATE -> if(canEdit) AnimalFormScreen(store, null, onBack = { ongScreen=OngScreen.ANIMALS }, onDone = { ongAnimalId=store.selected?.id; ongScreen=OngScreen.DETAILS })
                OngScreen.EDIT -> if(canEdit) { val animal=store.selected; if(animal!=null) AnimalFormScreen(store, animal, onBack = { ongScreen=OngScreen.DETAILS }, onDone = { ongScreen=OngScreen.DETAILS }) else OngAnimalDetailsScreen(store, false, onBack = { ongScreen=OngScreen.ANIMALS }, onEdit = {}) }
            }
            }
        }
        authenticated != null || (state is SessionState.SignedOut && screen == Screen.HOME) -> {
            when {
                authenticated != null && screen == Screen.SPONSOR && pendingSponsor != null && supportStore != null -> SponsorForm(supportStore,publicApi,pendingSponsor!!,onBack={returnAnimal=pendingSponsor;screen=Screen.HOME},onLogout=::logout,onMySupports={screen=Screen.MY_SUPPORTS},navigation=supportNavigation)
                authenticated != null && screen == Screen.MY_SUPPORTS && supportStore != null -> SupportExperience(supportStore,onBack={returnAnimal=null;screen=Screen.HOME},onLogout=::logout,navigation=supportNavigation,onCatalog={screen=Screen.ANIMAIS})
                authenticated != null && screen == Screen.MEDICAL_PERSONAL && medicalStore != null -> ProntuarioExperience(medicalStore,onBack={screen=Screen.MY_ADOPTIONS},onLogout=::logout,onCatalog={screen=Screen.ANIMAIS},onHome={screen=Screen.HOME})
                authenticated != null && screen == Screen.DONATE && donationOng != null && financeStore != null -> DonationForm(financeStore,donationOng!!,donationName,onBack={returnAnimal=donationAnimal;screen=Screen.HOME},onLogout=::logout,onMyDonations={screen=Screen.MY_DONATIONS},animalId=donationAnimal,onCatalog={returnAnimal=null;screen=Screen.ANIMAIS},onHome={returnAnimal=null;screen=Screen.HOME})
                authenticated != null && screen == Screen.MY_DONATIONS && financeStore != null -> FinancialExperience(financeStore,onBack={returnAnimal=null;screen=Screen.HOME},onLogout=::logout,onCatalog={returnAnimal=null;screen=Screen.ANIMAIS},onHome={returnAnimal=null;screen=Screen.HOME})
                authenticated != null && screen == Screen.REQUEST && pendingAdoption != null && adoptionStore != null -> AdoptionRequestScreen(adoptionStore, publicApi, pendingAdoption!!, onBack = { returnAnimal = pendingAdoption; screen = Screen.HOME }, onCatalog = { returnAnimal = null; screen = Screen.ANIMAIS }, onHome = { returnAnimal = null; screen = Screen.HOME }, onLogout = ::logout, onMyRequests = { returnAnimal = null; screen = Screen.MY_ADOPTIONS })
                authenticated != null && screen == Screen.MY_ADOPTIONS && adoptionStore != null -> AdoptionExperience(adoptionStore, onBack = { returnAnimal = null; screen = Screen.HOME }, onLogout = ::logout, onCatalog = { returnAnimal = null; screen = Screen.ANIMAIS }, onMedical = { animalId, name ->
                    medicalAnimalId=animalId;medicalAnimalName=name;medicalAnimalStatus=AnimalStatus.ADOTADO;screen=Screen.MEDICAL_PERSONAL
                })
                else -> PublicAnimalsExperience(publicApi, authenticated != null, onAccount = { if(authenticated != null) logout() else screen = Screen.LOGIN }, onAdopt = { id -> pendingSponsor=null;donationOng=null;pendingAdoption = id; screen = if(authenticated != null) Screen.REQUEST else Screen.LOGIN }, onMyRequests = { returnAnimal = null; screen = Screen.MY_ADOPTIONS }, initialAnimalId = returnAnimal, initialHome = screen != Screen.ANIMAIS, onDonate={ animal -> donationOng=animal.ongId;donationName=animal.ongNome.orEmpty();donationAnimal=animal.id;pendingAdoption=null;pendingSponsor=null;screen=if(authenticated!=null)Screen.DONATE else Screen.LOGIN }, onMyDonations={screen=Screen.MY_DONATIONS},onSponsor={id->pendingSponsor=id;pendingAdoption=null;donationOng=null;screen=if(authenticated!=null)Screen.SPONSOR else Screen.LOGIN},onMySupports={screen=Screen.MY_SUPPORTS},onTransparency={openTransparency(it)},onGuestAccount={id->pendingAdoption=null;pendingSponsor=null;donationOng=null;donationAnimal=null;returnAnimal=id;screen=Screen.LOGIN})
            }
        }
        screen == Screen.PRIMEIRO_ACESSO && state is SessionState.SignedOut -> {
            BackHandler { screen = Screen.LOGIN }
            FirstAccessScreen(authApi, onBack = { screen = Screen.LOGIN }, onSuccess = { screen = Screen.LOGIN; toast("Senha definida. Entre com seu e-mail e senha.") })
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
                onExplore = { screen = Screen.HOME },
                onFirstAccess = { if (state is SessionState.SignedOut) screen = Screen.PRIMEIRO_ACESSO },
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


private data class DrawerDestination(val label:String,val screen:OngScreen,val icon:ImageVector)

@Composable
private fun InstitutionalDrawer(user:UserIdentity, current:OngScreen, onNavigate:(OngScreen)->Unit, content:@Composable ()->Unit) {
    val drawer = key(user.id, user.ongId) { rememberDrawerState(DrawerValue.Closed) }
    val scope = rememberCoroutineScope()
    val entries = buildList {
        add(DrawerDestination("Painel inicial",OngScreen.HOME,Icons.Default.Home))
        if(user.hasOng) {
            add(DrawerDestination("Animais",OngScreen.ANIMALS,Icons.Default.Star))
            add(DrawerDestination("Prontuários pelos animais",OngScreen.ANIMALS,Icons.Default.Favorite))
            if(canManageAdoptions(user.perfil)) add(DrawerDestination("Solicitações de adoção",OngScreen.ADOPTIONS,Icons.Default.Favorite))
            if(canManageEmployees(user.perfil)) add(DrawerDestination("Funcionários",OngScreen.EMPLOYEES,Icons.Default.Person))
            if(canManageFinance(user.perfil)) add(DrawerDestination("Doações e financeiro",OngScreen.FINANCE,Icons.Default.ShoppingCart))
            if(canManageSupport(user.perfil)) add(DrawerDestination("Apadrinhamentos",OngScreen.SUPPORTS,Icons.Default.Star))
            if(user.perfil == UserProfile.ADMINISTRADOR) {
                add(DrawerDestination("Relatórios financeiros",OngScreen.REPORT,Icons.Default.Info))
                add(DrawerDestination("Transparência",OngScreen.TRANSPARENCY,Icons.Default.Info))
            }
        }
    }
    ModalNavigationDrawer(drawerState=drawer,drawerContent={
        ModalDrawerSheet(drawerContainerColor=PU.Paper) {
            Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal=12.dp)) {
                Text("Organização",color=PU.Foliage,style=MaterialTheme.typography.labelLarge,modifier=Modifier.padding(start=16.dp,top=24.dp))
                Text(user.ongId?.let { "Área da ONG" } ?: "ONG não vinculada",color=PU.Green,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,modifier=Modifier.padding(start=16.dp,top=8.dp))
                Text(user.perfil.name.lowercase().replaceFirstChar{it.uppercase()},color=PU.Muted,modifier=Modifier.padding(start=16.dp,top=4.dp,bottom=16.dp))
                HorizontalDivider(color=PU.Border)
                entries.forEach { entry ->
                    val selected = when(entry.screen) {
                        OngScreen.ANIMALS -> entry.label == "Animais" && current in listOf(OngScreen.ANIMALS,OngScreen.DETAILS,OngScreen.CREATE,OngScreen.EDIT) || entry.label.startsWith("Prontuários") && current == OngScreen.MEDICAL
                        else -> entry.screen == current
                    }
                    NavigationDrawerItem(label={Text(entry.label)},selected=selected,onClick={ scope.launch { drawer.close(); if(current != entry.screen) onNavigate(entry.screen) } },icon={Icon(entry.icon,null)},colors=NavigationDrawerItemDefaults.colors(selectedContainerColor=PU.CardGreen,selectedTextColor=PU.Green,selectedIconColor=PU.Green),modifier=Modifier.padding(vertical=2.dp))
                }
            }
        }
    }) {
        CompositionLocalProvider(LocalInstitutionalMenu provides { scope.launch { drawer.open() }; Unit }) {
            Box(Modifier.fillMaxSize()) { content() }
            BackHandler(enabled=drawer.isOpen) { scope.launch { drawer.close() } }
        }
    }
}
