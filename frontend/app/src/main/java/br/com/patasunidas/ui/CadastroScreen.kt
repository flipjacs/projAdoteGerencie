package br.com.patasunidas.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable

@Composable
fun CadastroScreen(submitting: Boolean, onRegister: (String, String, String) -> Unit, onBack: () -> Unit, onMessage: (String) -> Unit) {
    RegistrationForm(submitting, onRegister, onBack, onMessage)
}
