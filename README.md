# Patas Unidas / SysAnimais

Projeto acadêmico de adoção e apadrinhamento de animais. O repositório contém uma API REST em Kotlin/Spring Boot e um aplicativo Android nativo em Kotlin/Jetpack Compose, no diretório [`frontend/`](frontend/). O backend usa Spring Security com JWT, JPA, Flyway e MySQL.

## O que já funciona

- Cadastro público com o perfil único `USUARIO`, sem escolha de perfil; login real com JWT e senha protegida por BCrypt.
- Cadastro de `ADMINISTRADOR`, `VOLUNTARIO` e `VETERINARIO` por administrador da própria ONG.
- Aplicativo com login, cadastro, tela inicial, lista de animais com filtros Todos/Cães/Gatos e detalhes de cada animal.
- Cinco animais demonstrativos locais: Thor, Mel, Nina, Simba e Bolinha. As fotos locais e suas licenças estão em [`frontend/PHOTO_CREDITS.md`](frontend/PHOTO_CREDITS.md). O módulo de animais existe no código local, mas ainda não foi implantado na API principal; adoções e apadrinhamentos continuam fora deste showcase.

## Estrutura

```text
src/main/kotlin/br/com/sysanimais/api/   API Spring Boot: auth, usuarios, security e config
src/main/resources/db/migration/          migrations Flyway
frontend/app/src/main/                    aplicativo Android Compose
compose.mysql-validation.yaml            MySQL isolado de desenvolvimento/teste
```

## Executar com Docker e emulador Android

**Pré-requisitos:** Docker com Compose, JDK 21, Android SDK e um emulador iniciado no Android Studio. Os comandos abaixo são para Linux, como o ambiente deste projeto. O Docker executa o **MySQL e a API**; o frontend é um APK Android e roda no emulador ou dispositivo, não como contêiner web.

### 1. MySQL no Docker

Na raiz do repositório, copie o exemplo se ainda não existir e preencha as três chaves `MYSQL_TEST_PASSWORD`, `MYSQL_TEST_ROOT_PASSWORD` e `JWT_SECRET` em `.env.mysql-validation`. Para cada uma, gere um valor com `openssl rand -hex 32`. O arquivo local é ignorado pelo Git.

```bash
cp -n .env.mysql-validation.example .env.mysql-validation
docker compose --project-name apisysanimais-validation \
  --env-file .env.mysql-validation -f compose.mysql-validation.yaml up -d
docker compose --project-name apisysanimais-validation \
  --env-file .env.mysql-validation -f compose.mysql-validation.yaml ps
```

Espere o serviço `db` aparecer como `healthy`. O banco usa um volume persistente próprio e a porta `3309` vinculada somente a `127.0.0.1`.

### 2. Backend em contêiner Docker

Gere o JAR localmente com JDK 21. O comando a seguir usa a imagem Java para executá-lo no Docker, com a rede do host Linux, de modo que alcance o MySQL em `127.0.0.1:3309` e atenda o emulador na porta `8081`.

```bash
export JAVA_HOME="$HOME/.jdks/jbr-21.0.11"
./gradlew bootJar
set -a
. ./.env.mysql-validation
set +a
export DB_PASSWORD="$MYSQL_TEST_PASSWORD"
docker run --rm --name apisysanimais-showcase-api --network host \
  --env DB_URL="jdbc:mysql://127.0.0.1:${MYSQL_TEST_PORT:-3309}/gestor_adocao_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC" \
  --env DB_USER=apisys_test --env DB_PASSWORD --env JWT_SECRET \
  --env SERVER_PORT=8081 --env SERVER_ADDRESS=127.0.0.1 --env SPRING_FLYWAY_ENABLED=true \
  -v "$PWD/build/libs/apisysanimais-0.1.0.jar:/app.jar:ro,Z" \
  eclipse-temurin:21-jre java -jar /app.jar
```

Deixe esse terminal aberto. Neste exemplo exclusivamente isolado, a opção explícita ativa o Flyway na inicialização. Antes de usá-la, confirme a URL e o esquema de teste: nunca a direcione ao banco principal. O `compose.mysql-validation.yaml` contém **apenas o MySQL**; não use `docker compose up` esperando que ele também inicie a API. A imagem Java poderá ser baixada na primeira execução. Use a porta `8081` livre; o projeto também pode ter outra instância de API na porta `8080`.

### 3. Frontend no emulador

Em outro terminal, com o emulador ligado no Android Studio:

```bash
cd frontend
export JAVA_HOME="$HOME/.jdks/jbr-21.0.11"
export ANDROID_HOME="$HOME/Android/Sdk"
./gradlew :app:installDebug
"$ANDROID_HOME/platform-tools/adb" shell am start -n br.com.patasunidas/.MainActivity
```

Configure o Gradle do Android Studio com **JDK 21**. O APK `debug` usa `http://10.0.2.2:8081/`, endereço pelo qual o emulador alcança a API no host. Para um dispositivo físico, altere `SERVER_ADDRESS` do comando Docker para `0.0.0.0`, permita o acesso local à porta `8081` e gere o APK com `./gradlew :app:installDebug -PpatasApiBaseUrl=http://IP_DO_COMPUTADOR:8081/`; `10.0.2.2` só funciona no emulador. HTTP sem TLS é permitido somente no build `debug`. O build `release` precisa de uma URL HTTPS válida antes de distribuição.

Abra o aplicativo, crie uma conta e entre com o e-mail e a senha cadastrados. Após o login, a aba **Animais** abre a listagem demonstrativa; tocar em um card abre seus detalhes. O JWT fica somente na memória do processo. A opção visual **Manter conectado** ainda não implementa persistência de sessão.

Para parar a API, use `Ctrl+C` no terminal do `docker run`. Para parar o MySQL **mantendo os dados do volume**:

```bash
docker compose --project-name apisysanimais-validation \
  --env-file .env.mysql-validation -f compose.mysql-validation.yaml down
```

## API e testes

### Implantação Docker com banco existente

O `Dockerfile` usa o JAR gerado localmente com JDK 21; `.dockerignore` limita o contexto ao JAR. `compose.yaml` referencia como externos o volume existente `apisysanimais_mysql_data` e a rede existente `apisysanimais_default`. Eles precisam existir antes de iniciar o Compose; não são criados nem apagados por essa configuração. O serviço MySQL usa a porta local 3308; a API usa a porta 8080 em todas as interfaces por compatibilidade. Para restringir a API ao host, configure `API_BIND_ADDRESS=127.0.0.1` no ambiente apropriado.

Use `.env.example` apenas como modelo para um `.env` local privado (`chmod 600 .env`); mantenha as credenciais e o JWT já configurados ao operar o banco existente. A URL JDBC fornecida pelo Compose é para desenvolvimento local e não oferece TLS. Para produção, siga as orientações de segurança abaixo.

Antes de usar Compose sobre uma instalação existente, confira volume, rede, containers e valores efetivos sem divulgá-los. A validação `docker compose --env-file .env -f compose.yaml config --quiet` não altera containers. O código atual desabilita Flyway por padrão; `SPRING_FLYWAY_ENABLED=true` o ativa e pode alterar o banco conectado. Nunca ative essa opção no principal sem autorização. A imagem já implantada tem comportamento próprio e continua executando Flyway na inicialização. Consulte o [runbook operacional](docs/operacao-b03.md) antes de preparar qualquer implantação.

O healthcheck usa `GET /actuator/health`, público somente nessa rota e sem detalhes de componentes. As demais rotas do Actuator continuam negadas. Verifique `docker inspect apisysanimais-api-1 --format '{{.State.Health.Status}}'` e os logs após a atualização.

| Rota | Acesso | Corpo principal |
| --- | --- | --- |
| `POST /api/usuarios/cadastro` | Público | `nome`, `email`, `senha`; o servidor atribui `USUARIO` |
| `POST /api/auth/login` | Público | `email`, `senha`; retorna `accessToken`, `tokenType`, `expiresIn` |
| `GET /api/auth/me` | JWT válido de usuário ativo | Retorna `id`, `nome`, `email`, `perfil` e `ongId` (nulo quando não vinculado) |
| `POST /api/usuarios/internos` | Administrador com JWT | Alias de provisionamento pendente: `nome`, `email`, `perfil`; rejeita `senha` e obtém a ONG do administrador |

O cadastro público rejeita `tipo`, `perfil` e `ongId` enviados pelo cliente. Respostas esperadas incluem `201` (cadastro), `400` (dados inválidos), `401` (sem autenticação ou login inválido), `403` (sem permissão) e `409` (e-mail duplicado). Senhas e hashes não são retornados.


### Configuração fora do desenvolvimento local

Os valores JDBC padrão são somente para desenvolvimento: não garantem TLS e usam `root`. Em produção, forneça `DB_URL` com TLS e validação da identidade do servidor MySQL configurados para o ambiente, `DB_USER` com os menores privilégios necessários e `DB_PASSWORD` por um gerenciador de segredos. O usuário que executa migrations pode precisar de privilégios diferentes dos da aplicação; planeje a separação operacional antes da implantação. Forneça também `JWT_SECRET` aleatório e protegido de pelo menos 32 bytes. Nunca versione credenciais. Estas orientações não configuram nem validam infraestrutura de produção automaticamente.

```bash
./gradlew test
cd frontend && ./gradlew :app:assembleDebug
```