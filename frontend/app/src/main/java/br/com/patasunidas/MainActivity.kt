package br.com.patasunidas

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import br.com.patasunidas.ui.HomeScreen
import br.com.patasunidas.ui.LoginScreen
import br.com.patasunidas.ui.CadastroScreen
import br.com.patasunidas.ui.PU
import br.com.patasunidas.ui.AnimalsScreen
import br.com.patasunidas.ui.AnimalDetailsScreen
import br.com.patasunidas.ui.AnimalFilter
import br.com.patasunidas.data.DemoAnimals
import br.com.patasunidas.data.AuthApi
import br.com.patasunidas.data.AuthSession
import br.com.patasunidas.data.InvalidCredentialsException
import br.com.patasunidas.data.EmailAlreadyUsedException
import br.com.patasunidas.data.InvalidRegistrationException
import kotlinx.coroutines.launch
import java.io.IOException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(PU.Green.toArgb()),
            navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        )
        setContent { PatasUnidasApp() }
    }
}

@Composable
fun PatasUnidasApp() {
    var screen by remember { mutableStateOf("login") }
    var session by remember { mutableStateOf<AuthSession?>(null) }
    var signingIn by remember { mutableStateOf(false) }
    var registering by remember { mutableStateOf(false) }
    var animalFilter by remember { mutableStateOf(AnimalFilter.ALL) }
    var selectedAnimalId by remember { mutableStateOf<String?>(null) }
    val authApi = remember { AuthApi(BuildConfig.API_BASE_URL) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun toast(message: String) =
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    val authenticated = session?.expiresAtMillis?.let { it > System.currentTimeMillis() } == true
    val selectedAnimal = DemoAnimals.byId(selectedAnimalId)

    when {
        screen == "home" && authenticated -> HomeScreen(
            onAnimals = { screen = "animais" },
            onAnimalClick = { selectedAnimalId = it; screen = "detalhes" },
            onSoon = { toast("Em breve — este é um protótipo demonstrativo") }
        )

        screen == "animais" && authenticated -> {
            BackHandler { screen = "home" }
            AnimalsScreen(
                filter = animalFilter,
                onFilterChange = { animalFilter = it },
                onAnimalClick = { selectedAnimalId = it; screen = "detalhes" },
                onHome = { screen = "home" },
                onSoon = { toast("Em breve — este é um protótipo demonstrativo") }
            )
        }

        screen == "detalhes" && authenticated && selectedAnimal != null -> {
            BackHandler { screen = "animais" }
            AnimalDetailsScreen(selectedAnimal, onBack = { screen = "animais" })
        }

        screen == "cadastro" && !authenticated -> {
            BackHandler { if (!registering) screen = "login" }
            CadastroScreen(
                submitting = registering,
                onRegister = { nome, email, senha ->
                    if (!registering) {
                        registering = true
                        scope.launch {
                            try {
                                authApi.register(nome, email, senha)
                                toast("Conta criada! Entre com seu e-mail e senha.")
                                screen = "login"
                            } catch (_: EmailAlreadyUsedException) {
                                toast("Este e-mail já está cadastrado")
                            } catch (_: InvalidRegistrationException) {
                                toast("Dados inválidos. Confira os campos.")
                            } catch (_: IOException) {
                                toast("Não foi possível conectar à API. Tente novamente.")
                            } catch (_: Exception) {
                                toast("Falha no cadastro. Tente novamente.")
                            } finally {
                                registering = false
                            }
                        }
                    }
                },
                onBack = { if (!registering) screen = "login" },
                onMessage = ::toast
            )
        }

        else -> {
            BackHandler { if (authenticated) screen = "home" }
            LoginScreen(
                onRegister = { screen = "cadastro" },
                onLogin = { email, senha, _ ->
                    if (!signingIn) {
                        if (email.isBlank() || senha.isBlank()) {
                            toast("Informe e-mail e senha")
                        } else {
                            signingIn = true
                            scope.launch {
                                try {
                                    session = authApi.login(email, senha)
                                    toast("Login realizado!")
                                    screen = "home"
                                } catch (_: InvalidCredentialsException) {
                                    toast("E-mail ou senha inválidos")
                                } catch (_: IOException) {
                                    toast("Não foi possível conectar à API. Tente novamente.")
                                } catch (_: Exception) {
                                    toast("Falha no login. Tente novamente.")
                                } finally {
                                    signingIn = false
                                }
                            }
                        }
                    }
                },
                onSoon = { toast("Em breve — este é um protótipo demonstrativo") }
            )
        }
    }
}
