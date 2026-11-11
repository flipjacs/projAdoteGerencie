# Patas Unidas / SysAnimais

Projeto acadêmico de adoção e apadrinhamento de animais. O repositório contém uma API REST em Kotlin/Spring Boot e um aplicativo Android nativo em Kotlin/Jetpack Compose, no diretório [`frontend/`](frontend/). O backend usa Spring Security com JWT, JPA, Flyway e MySQL.

## O que já funciona

- Cadastro público com o perfil único `USUARIO`, sem escolha de perfil; login real com JWT e senha protegida por BCrypt.
- Cadastro de `ADMINISTRADOR`, `VOLUNTARIO` e `VETERINARIO` por administrador da própria ONG.
- Aplicativo com login, cadastro, tela inicial, lista de animais com filtros Todos/Cães/Gatos e detalhes de cada animal.
- Cinco animais demonstrativos locais: Thor, Mel, Nina, Simba e Bolinha. As fotos locais e suas licenças estão em [`frontend/PHOTO_CREDITS.md`](frontend/PHOTO_CREDITS.md). Ainda não existe endpoint de animais; adoções, apadrinhamentos e as demais áreas permanecem fora deste showcase.

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
  --env SERVER_PORT=8081 --env SERVER_ADDRESS=127.0.0.1 \
  -v "$PWD/build/libs/apisysanimais-0.1.0.jar:/app.jar:ro,Z" \
  eclipse-temurin:21-jre java -jar /app.jar
```

Deixe esse terminal aberto. Na primeira inicialização, o Flyway cria e atualiza o esquema automaticamente. O `compose.mysql-validation.yaml` contém **apenas o MySQL**; não use `docker compose up` esperando que ele também inicie a API. A imagem Java poderá ser baixada na primeira execução. Use a porta `8081` livre; o projeto também pode ter outra instância de API na porta `8080`.

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

Antes de usar Compose sobre uma instalação existente, confira o volume, a rede, os containers e os valores efetivos sem divulgá-los. Um container anterior de recuperação pode ter rótulos do mesmo projeto Compose: avalie o plano de substituição antes de usá-lo. A validação `docker compose --env-file .env -f compose.yaml config --quiet` não altera containers. Para preparar a imagem, execute `JAVA_HOME=/caminho/do/jdk21 ./gradlew test bootJar` e `docker compose --env-file .env -f compose.yaml build api`. Após revisar o impacto no ambiente, `docker compose --env-file .env -f compose.yaml up -d --no-deps api` destina-se a atualizar apenas a API; não execute `down -v`. Antes de qualquer atualização, confira o histórico Flyway e as migrations presentes no JAR: a API executa migrations pendentes ao iniciar.

O healthcheck usa `GET /actuator/health`, público somente nessa rota e sem detalhes de componentes. As demais rotas do Actuator continuam negadas. Verifique `docker inspect apisysanimais-api-1 --format '{{.State.Health.Status}}'` e os logs após a atualização.

| Rota | Acesso | Corpo principal |
| --- | --- | --- |
| `POST /api/usuarios/cadastro` | Público | `nome`, `email`, `senha`; o servidor atribui `USUARIO` |
| `POST /api/auth/login` | Público | `email`, `senha`; retorna `accessToken`, `tokenType`, `expiresIn` |
| `GET /api/auth/me` | JWT válido de usuário ativo | Retorna `id`, `nome`, `email`, `perfil` e `ongId` (nulo quando não vinculado) |
| `POST /api/usuarios/internos` | Administrador com JWT | `nome`, `email`, `senha`, `perfil` interno; ONG vem do administrador autenticado |

O cadastro público rejeita `tipo`, `perfil` e `ongId` enviados pelo cliente. Respostas esperadas incluem `201` (cadastro), `400` (dados inválidos), `401` (sem autenticação ou login inválido), `403` (sem permissão) e `409` (e-mail duplicado). Senhas e hashes não são retornados.

`GET /api/auth/me` exige `Authorization: Bearer <token>`; tokens ausentes, inválidos ou expirados recebem 401, e uma conta desativada ou removida recebe 403. O perfil e a ONG retornados são os atuais no banco, não valores fornecidos pelo cliente. Outras violações de integridade recebem erro 500 genérico, sem detalhes SQL.

O login reserva capacidade **antes do BCrypt**, inclusive para tentativas válidas: até 30 requisições por origem em 60 segundos, 8 por origem e e-mail normalizado em 5 minutos e 120 no total da instância em 60 segundos. Acima do limite, retorna `429` genérico com `Retry-After` em segundos. Janelas são fixas, a expiração é verificada nas próximas requisições e os mapas têm até 4096 origens e 4096 pares; quando cheios, novas chaves são recusadas até haver espaço. O estado fica apenas na memória, reinicia com a aplicação e não é compartilhado entre réplicas. Essa política pode limitar usuários atrás do mesmo NAT e, sob saturação global, negar logins legítimos.

O endereço usado é `remoteAddr`; cabeçalhos encaminhados por clientes não são confiados diretamente. Sem um limite global por conta, origens distribuídas ainda podem tentar o mesmo e-mail até o teto global. Um limite exclusivo por e-mail permitiria bloquear contas de terceiros. Em produção com proxy ou várias instâncias, configure origens confiáveis e uma proteção compartilhada na borda com controles de abuso, monitoramento e políticas adequadas; o limitador local não substitui essa proteção.

### Configuração fora do desenvolvimento local

Os valores JDBC padrão são somente para desenvolvimento: não garantem TLS e usam `root`. Em produção, forneça `DB_URL` com TLS e validação da identidade do servidor MySQL configurados para o ambiente, `DB_USER` com os menores privilégios necessários e `DB_PASSWORD` por um gerenciador de segredos. O usuário que executa migrations pode precisar de privilégios diferentes dos da aplicação; planeje a separação operacional antes da implantação. Forneça também `JWT_SECRET` aleatório e protegido de pelo menos 32 bytes. Nunca versione credenciais. Estas orientações não configuram nem validam infraestrutura de produção automaticamente.

```bash
./gradlew test
cd frontend && ./gradlew :app:assembleDebug
```

Os testes automatizados da API usam H2 em modo MySQL. O fluxo acima permite também testar com MySQL real. Não execute novamente o script acadêmico `mysql_ong_scrpit.sql` sobre um banco existente: ele começa removendo o banco. As migrations Flyway preservam os dados, inclusive ao unificar os perfis públicos antigos na versão V3.

### Animais (RF14/RF15; implantação pendente)

A migration V4 cria `animal` e `animal_foto`. Não inicie esta versão apontando para o banco principal antes de autorizar e planejar a aplicação da V4: o Flyway está habilitado na inicialização. A imagem Docker atualmente implantada não contém este módulo.

A API obtém a ONG do JWT autenticado. Administrador e voluntário criam e alteram; veterinário também consulta; usuário público acessa somente o catálogo. Operações internas fora da ONG retornam 404; perfis insuficientes, 403. A listagem interna usa `page` (zero inicial), `size` (1–100; padrão 20) e `ativo` opcional. O cadastro começa ativo, não publicado e em tratamento. Inativação é lógica e idempotente; não há reativação.

Estados: `EM_TRATAMENTO` → `DISPONIVEL` ou `FALECIDO`; `DISPONIVEL` → `EM_TRATAMENTO`, `EM_PROCESSO_ADOCAO` ou `FALECIDO`; `EM_PROCESSO_ADOCAO` → `DISPONIVEL`, `EM_TRATAMENTO` ou `FALECIDO`. Repetir o mesmo estado é idempotente. `FALECIDO` e `ADOTADO` são terminais; não é possível marcar `ADOTADO` diretamente, pois falta o fluxo formal de adoção. Apadrinhamento será vínculo independente, não estado do animal. Situação e última pessoa/data da alteração são registradas; não existe histórico completo de transições.

`POST /api/animais` e `PUT /api/ong/animais/{id}` aceitam JSON com `nome`, `especie` (`CAO`, `GATO`, `OUTRO`), `sexo` (`MACHO`, `FEMEA`, `DESCONHECIDO`), `porte` (`PEQUENO`, `MEDIO`, `GRANDE`, `DESCONHECIDO`), `dataEntrada` (ISO 8601) e os opcionais `nascimentoEstimado`, `descricao`, `historia`, `dataResgate`, `origemResgate`, `observacoesResgate`. PUT substitui todos os campos editáveis; inclua todos os que deseja preservar. ONG, situação, publicação e controle são definidos pelo servidor. Consulte `GET /api/ong/animais[/{id}]`; use `PATCH /api/ong/animais/{id}/situacao` com `{"situacao":"DISPONIVEL"}` e `PATCH /api/ong/animais/{id}/inativacao` sem corpo.

`POST /api/ong/animais/{id}/fotos` recebe multipart `arquivo`, até 5 MiB, JPEG ou PNG válido, até 16 milhões de pixels e 10 fotos por animal. O servidor gera o nome, ignora o nome enviado e marca a primeira foto como principal. Os DTOs incluem URLs de leitura autenticada (`GET /api/ong/animais/{id}/fotos/{fotoId}`). O catálogo `GET /api/animais` só inclui registros ativos, disponíveis e publicados; fotos públicas usam `GET /api/animais/{id}/fotos/{fotoId}` com os mesmos critérios. Não há endpoint de publicação nesta fase.

Fotografias são arquivos locais, com metadados no MySQL; configure `APP_ANIMAL_PHOTOS_DIR` para diretório persistente e gravável pelo usuário 65534 do container, e monte um volume dedicado antes da implantação. Sem volume, arquivos serão perdidos na recriação. Backup/restauração deve abranger simultaneamente banco e diretório de fotos; interrupções de processo podem deixar arquivos órfãos, exigindo reconciliação operacional. Nunca monte diretórios de código ou arquivos sensíveis como armazenamento de fotos.

#### Controle de concorrência dos animais

As respostas internas de animal incluem `versao` (inteiro de 64 bits). Para `PUT /api/ong/animais/{id}`, envie todos os campos editáveis e a `versao` recebida; para `PATCH /api/ong/animais/{id}/situacao`, envie `{"situacao":"DISPONIVEL","versao":0}` com a versão atual. Versão ausente, negativa ou não inteira retorna 400. Uma versão anterior retorna 409 com `detail`: “Este animal foi atualizado por outra pessoa. Recarregue os dados antes de salvar novamente.” Em caso de 409 consulte novamente o animal e permita ao usuário revisar os dados; não reenvie automaticamente. O Android atual ainda não envia versão e precisa ser atualizado antes da integração com esta API.

O Hibernate incrementa a versão após alterações do animal, inclusive situação, inativação e associação de foto. O upload não exige versão: é serializado pelo bloqueio do animal, que também preserva o limite de fotos e a consistência com inativação. Inativação continua idempotente e não exige versão. A versão retornada por uma operação de escrita é a versão após a gravação. A V4 já possui a coluna necessária; não há migration adicional.
