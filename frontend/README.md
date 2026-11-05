# Aplicativo Android Patas Unidas

Aplicativo nativo Kotlin/Jetpack Compose integrado à API SysAnimais para cadastro público e login com JWT. Depois de autenticar, o usuário vê a tela inicial, a listagem filtrável de cinco animais demonstrativos e os detalhes de cada animal. Os dados e as fotos são locais; consulte [`PHOTO_CREDITS.md`](PHOTO_CREDITS.md). Não há listagem de animais na API nesta etapa.

O passo a passo para iniciar **MySQL e backend no Docker** e instalar o APK no emulador está no [README principal](../README.md#executar-com-docker-e-emulador-android). O Android não roda como um frontend web em contêiner: o APK é executado no emulador ou dispositivo.

Com o backend na porta `8081`, emulador ligado e JDK 21 configurado:

```bash
export JAVA_HOME="$HOME/.jdks/jbr-21.0.11"
export ANDROID_HOME="$HOME/Android/Sdk"
./gradlew :app:installDebug
"$ANDROID_HOME/platform-tools/adb" shell am start -n br.com.patasunidas/.MainActivity
```

O build `debug` usa `http://10.0.2.2:8081/` para alcançar o host a partir do emulador. Para um dispositivo físico, informe `-PpatasApiBaseUrl=http://IP_DO_COMPUTADOR:8081/` ao Gradle e disponibilize a API na rede local. O JWT fica apenas na memória do processo; **Manter conectado** é um controle visual ainda sem persistência. O build `release` requer URL HTTPS válida.
