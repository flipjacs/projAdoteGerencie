# Aplicativo Android Patas Unidas

Aplicativo nativo Kotlin/Jetpack Compose integrado à API SysAnimais para cadastro público e login com JWT. Depois de autenticar, o usuário vê a tela inicial, a listagem filtrável de cinco animais demonstrativos e os detalhes de cada animal. Os dados e as fotos são locais; consulte [`PHOTO_CREDITS.md`](PHOTO_CREDITS.md). Não há listagem de animais na API nesta etapa.

O passo a passo para iniciar **MySQL e backend no Docker** e instalar o APK no emulador está no [README principal](../README.md#executar-com-docker-e-emulador-android). O Android não roda como um frontend web em contêiner: o APK é executado no emulador ou dispositivo.

Com o backend na porta `8080`, emulador ligado e JDK 21 configurado:

```bash
export JAVA_HOME="$HOME/.jdks/jbr-21.0.11"
export ANDROID_HOME="$HOME/Android/Sdk"
./gradlew :app:installDebug
"$ANDROID_HOME/platform-tools/adb" shell am start -n br.com.patasunidas/.MainActivity
```

Se usar o fluxo alternativo do README principal que inicia a API na porta `8081`, instale com `./gradlew :app:installDebug -PpatasApiBaseUrl=http://10.0.2.2:8081/`.

O build `debug` usa `http://10.0.2.2:8080/` para alcançar a API do host a partir do emulador. Para um dispositivo físico, informe `-PpatasApiBaseUrl=http://IP_DO_COMPUTADOR:8080/` ao Gradle e disponibilize a API em rede local confiável; HTTP sem TLS não deve ser usado em redes não confiáveis. O build `release` requer URL HTTPS válida.

Após login, o aplicativo consulta `/api/auth/me` antes de abrir a área pública ou da equipe. O JWT e a identidade ficam apenas na memória; **Manter conectado** ainda não persiste a sessão. Sair remove a sessão local, mas não revoga no servidor um JWT já copiado, que pode continuar válido até expirar. Se a consulta de identidade falhar por rede, é possível repeti-la sem fazer novo login.

Se o login receber `429`, a tela informa a espera e desabilita novos envios até o prazo. O `Retry-After` numérico é limitado a 900 segundos; se estiver ausente ou inválido, usa-se uma espera de 30 segundos. O app não envia nova tentativa automaticamente e o servidor continua responsável por aplicar seu próprio limite.

### Animais da ONG (RF14/RF15)

A área da ONG usa o backend do módulo de animais. A imagem principal da API ainda não possui este módulo e o banco principal não está na V4. Para integração local, use um backend temporário apontando explicitamente para um esquema isolado V1–V4; por exemplo, compile o APK debug com `./gradlew assembleDebug -PpatasApiBaseUrl=http://10.0.2.2:18081/`. Nunca inicie o novo backend apontando para `gestor_adocao` antes da autorização para a V4.

Administrador e voluntário cadastram e alteram animais; veterinário pode consultar. Fotos são selecionadas com o seletor do Android e transmitidas separadamente após a criação. O JWT permanece em memória; imagens internas são buscadas com Authorization e sem cache público. Os módulos de publicação, adoção e apadrinhamento não foram implementados.

A edição e a alteração de situação enviam a `versao` obtida da API. Em conflito de versão, o formulário conserva o rascunho e sua versão original; é possível consultar as diferenças e descartar o rascunho apenas mediante confirmação. Não há reenvio automático. Após upload, os detalhes são consultados novamente para receber a versão atual. O contrato exige o backend da Fase 1.3.1; a API principal implantada ainda não possui o módulo de animais.

Conflitos de versão são reconhecidos pelo código `ANIMAL_VERSION_CONFLICT` no HTTP 409. Mensagens alteradas não modificam a identificação; códigos ausentes ou desconhecidos são tratados como conflito genérico.
