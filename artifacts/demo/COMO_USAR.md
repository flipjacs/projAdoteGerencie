# Demonstração local Patas Unidas

O ambiente atual usa apenas um banco MySQL isolado (`patas-demo-mvp`, schema `gestor_adocao_test`) na porta `13339`; a API Kotlin fica em `18084`. O APK do emulador aponta para `http://10.0.2.2:18084/`.

## Contas

Consulte [ACESSOS.md](ACESSOS.md). Todas usam a senha de demonstração indicada ali. Não use essas contas fora deste ambiente local.

## Iniciar após reiniciar o computador

Na raiz `APIsysAnimais`:

```bash
MYSQL_TEST_PORT=13339 docker compose \
  --project-name patas-demo-mvp \
  --env-file .env.mysql-validation \
  -f compose.mysql-validation.yaml up -d
```

Em um terminal, inicie a API:

```bash
cd /home/flipjacs/IdeaProjects/APIsysAnimais
set -a
. ./.env.mysql-validation
set +a
unset BOOTSTRAP_ADMIN_PASSWORD BOOTSTRAP_ADMIN_ONG_ID BOOTSTRAP_ADMIN_NAME BOOTSTRAP_ADMIN_EMAIL
export JAVA_HOME=/home/flipjacs/.jdks/jbr-21.0.11
export PATH="$JAVA_HOME/bin:$PATH"
export DB_URL='jdbc:mysql://127.0.0.1:13339/gestor_adocao_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
export DB_USER=apisys_test
export DB_PASSWORD="$MYSQL_TEST_PASSWORD"
export SERVER_PORT=18084
export SERVER_ADDRESS=127.0.0.1
export SPRING_FLYWAY_ENABLED=true
java -jar build/libs/apisysanimais-0.1.0.jar
```

Confirme em outro terminal:

```bash
curl http://127.0.0.1:18084/actuator/health
```

No AVD `Medium_Phone_API_37.0`, abra o Patas Unidas já instalado. Se precisar reinstalar:

```bash
/home/flipjacs/Android/Sdk/platform-tools/adb install -r \
  /home/flipjacs/IdeaProjects/APIsysAnimais/frontend/app/build/outputs/apk/debug/app-debug.apk
/home/flipjacs/Android/Sdk/platform-tools/adb shell monkey \
  -p br.com.patasunidas -c android.intent.category.LAUNCHER 1
```

## Dados incluídos

Duas ONGs, nove contas cobrindo os cinco perfis, sete animais (cinco com fotos), solicitações de adoção pendente/rejeitada/concluída, prontuário de animal adotado, apadrinhamentos ativo/cancelado/encerrado e parcelas confirmadas/pendentes, doações e despesas em estados diferentes, dois demonstrativos publicados e um rascunho privado.

Para encerrar a API, use `Ctrl+C` no terminal em que ela estiver rodando. Para parar apenas o MySQL mantendo o volume e os dados, use o mesmo comando Compose com `down` (sem `-v`). Não exponha a porta MySQL `13339` nem use estas contas em produção.
