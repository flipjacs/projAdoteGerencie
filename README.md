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

### Gestão de funcionários (B1.3; não implantada)

Um `ADMINISTRADOR` ativo e com credencial regular de uma ONG ativa gerencia somente a equipe da própria ONG, em `/api/ong/funcionarios`. `POST` recebe `{"nome":"...","email":"...","perfil":"VOLUNTARIO"}`; também aceita `ADMINISTRADOR` e `VETERINARIO`. Não recebe senha, ONG, flags ou campos internos. A nova conta é criada inativa e com credencial pendente; o hash de um segredo aleatório é descartado e não permite acesso antes da B1.4. O antigo `POST /api/usuarios/internos` encaminha ao mesmo fluxo seguro e rejeita o corpo antigo que continha `senha`. O bootstrap legado de administrador de ONG também está desativado: configurar seus antigos parâmetros faz a inicialização falhar.

`GET /api/ong/funcionarios?page=0&size=20` lista apenas a ONG atual, com máximo de 100 itens; `GET /{id}` consulta. `PUT /{id}` recebe `nome` e `versao`. `PATCH /{id}/perfil` recebe `perfil`, `versao` e `motivo`; `PATCH /{id}/desativacao` recebe `versao` e `motivo`; `PATCH /{id}/reativacao` recebe `versao` e, opcionalmente, `motivo`. O e-mail não é editável nesta fase. As respostas incluem `id`, `ongId`, `nome`, `email`, `perfil`, `ativo`, `credencialPendente` e `versao`; nunca incluem hash ou credencial. A versão precisa ser inteira e atual; alterações obsoletas recebem 409. Repetições com versão atual que não mudam estado não criam auditoria adicional.

Erros da equipe incluem `FUNCIONARIO_NAO_ENCONTRADO` (404), `EMAIL_ALREADY_REGISTERED` (409), `PERFIL_INVALIDO` (400), `FUNCIONARIO_VERSAO_CONFLITANTE` e `ULTIMO_ADMINISTRADOR` (409). Falhas de autenticação/ONG suspensa no perímetro usam os códigos genéricos existentes para não revelar estado de outra pessoa. Os detalhes dos motivos não devem conter segredos ou dados pessoais desnecessários.

A desativação incrementa `token_versao`, invalidando JWTs anteriores mesmo após reativação. Tokens antigos sem a claim são aceitos somente enquanto a versão de token persistida for zero. Mudança de perfil não incrementa essa versão: permissões continuam sendo consultadas no banco a cada requisição. A B1.4 deverá incrementar ou atualizar esse controle ao redefinir credenciais. Alterações diretas por SQL não executam essa regra e não devem ser usadas como mecanismo de gerenciamento.

Operações de escrita bloqueiam primeiro a ONG, depois o administrador e a conta-alvo; isso serializa alterações que poderiam remover o último administrador ativo e com credencial regular e coordena com a suspensão. A auditoria de equipe é transacional e registra IDs, operação, dados anteriores/posteriores relevantes e motivo; alterações de nome incluem o nome anterior e posterior, de modo que o acesso e a retenção dos registros exigem proteção. Não há endpoint para editar ou excluir auditorias.

V6 adiciona `versao` e `token_versao` aos usuários, índice de equipe e tabela de auditoria. Não foi adicionada constraint de perfil/ONG porque é necessário verificar e sanear dados legados antes de impô-la. A V6, como V4–V5, ainda não foi aplicada no principal. Avalie o impacto do contrato antigo e dos parâmetros de bootstrap antes de qualquer implantação futura.

Como pré-verificação somente de leitura, operadores autorizados podem contar vínculos incompatíveis sem exibir identidades: `SELECT COUNT(*) FROM usuario WHERE (LOWER(perfil) IN ('administrador','voluntario','veterinario') AND ong_id IS NULL) OR (LOWER(perfil) = 'admin_plataforma' AND ong_id IS NOT NULL);`. O resultado não saneia os registros; a correção requer avaliação e autorização separadas. A V6 não os exclui nem impõe uma constraint que impeça a migração desses legados.

O cadastro público possui limite independente do login: até 10 requisições por origem e 100 na instância por janela de 60 segundos, incluindo entradas inválidas. Ao exceder, retorna `429` com `Retry-After` e código `CADASTRO_RATE_LIMIT`. A origem é `remoteAddr`, sem confiar em `X-Forwarded-For`; clientes atrás do mesmo NAT compartilham o limite. O estado fica em memória por instância e é perdido ao reiniciar. O `409` para duplicidade permanece para compatibilidade e contém o código `EMAIL_ALREADY_REGISTERED`: portanto o limite reduz abuso, mas não impede enumeração de contas. Uma resposta indistinguível exige decisão de produto e fluxo de confirmação apropriado.

O logger Hibernate `SqlExceptionHelper` está desabilitado para evitar mensagens SQL que podem conter dados pessoais. Falhas de integridade no cadastro são registradas com SQLState validado e código numérico, sem mensagem, valores ou exceção completa. Não habilite logs SQL ou de parâmetros em produção sem revisão; outros componentes e configurações ainda podem produzir informações sensíveis.

`GET /api/auth/me` exige `Authorization: Bearer <token>`; tokens ausentes, inválidos ou expirados recebem 401, e uma conta desativada ou removida recebe 403. O perfil e a ONG retornados são os atuais no banco, não valores fornecidos pelo cliente. Outras violações de integridade recebem erro 500 genérico, sem detalhes SQL.

O perfil `ADMIN_PLATAFORMA` é global e não deve ter ONG. Ele pode autenticar e consultar `/api/auth/me`, mas não recebe os privilégios de `ADMINISTRADOR` de ONG nem acesso às rotas internas das ONGs. Não existe endpoint público de criação de administrador global. O cadastro público continua criando apenas `USUARIO`; o cadastro interno permite apenas perfis internos e usa a ONG do administrador ativo. Gravações JPA verificam que perfis internos tenham ONG e que o perfil global não tenha; registros legados sem ONG permanecem bloqueados nas operações internas. A coluna textual existente comporta o perfil global. Alterações SQL diretas não passam pela validação JPA, portanto uma constraint e o saneamento de dados legados devem ser avaliados antes de introduzir outros escritores. Clientes antigos podem não reconhecer o novo valor de `perfil` e precisam ser atualizados antes de usar contas globais.

### Administração institucional (B1.2; não implantada)

No código local, `ADMIN_PLATAFORMA` acessa `/api/plataforma/ongs`: `POST` cria ONG e administrador inicial; `GET` lista (parâmetros `page` e `size`) ou consulta `/{id}`; `PUT /{id}` substitui dados institucionais e exige a `versao` consultada, retornando 409 para versão antiga; `PATCH /{id}/suspensao` exige `{"motivo":"..."}`; `PATCH /{id}/reativacao` reativa. Criação recebe `nome`, `email`, opcionais `identificacao`, `telefone`, `cidade`, `estado` e `administrador` com `nome` e `email`. A identificação, quando informada, deve conter 11 ou 14 dígitos sem formatação; o cálculo de dígitos verificadores não é feito nesta fase. O administrador inicial fica inativo e com `credencial_pendente=true`, além de receber hash de segredo aleatório descartado. O login e os tokens são bloqueados enquanto a credencial estiver pendente, mesmo que a conta seja marcada ativa. B1.4 precisará definir o fluxo seguro para ativá-la. Respostas incluem identificação, situação, datas e versão; duplicidades retornam 409. Ações repetidas de suspensão ou reativação não duplicam o evento quando o estado não muda.

A suspensão bloqueia login e tokens anteriores de funcionários, e oculta animais e fotos públicas sem apagar a publicação. Reativar não ativa contas individuais. Escritas internas obtêm bloqueio compartilhado da ONG dentro da transação; a suspensão aguarda operações em andamento e operações posteriores verificam a situação. Autorizações e acesso ao catálogo fazem consultas adicionais; não há cache. O código de negação é `ACESSO_NEGADO` com 403, sem identificar a causa. A auditoria institucional grava ator, organização, operação, estados ou versões, motivo e conta alvo quando aplicável, sem credenciais. Não há endpoint para alterar auditorias; privilégios diretos de banco ainda exigem controle operacional.

V5 adiciona situação (`ATIVA` para legados, preservando funcionamento), auditoria, versão e identificação fiscal única. Datas de criação e atualização desconhecidas de ONGs legadas permanecem nulas, sem inventar datas históricas. Antes de planejar sua aplicação, confira valores `cnpj_cpf` duplicados, inclusive equivalência pela collation, e faça backup: DDL MySQL não é totalmente transacional e uma falha pode deixar alterações parciais. Não aplique V4/V5 no principal sem autorização específica. A imagem principal ainda não contém estas alterações.

O bootstrap global é um procedimento offline explicitamente habilitado, nunca um endpoint. Exige banco MySQL já migrado, `DB_URL` explícita, `SERVER_PORT=-1`, Flyway desativado, `APP_BOOTSTRAP_GLOBAL_ENABLED=true`, `APP_BOOTSTRAP_GLOBAL_CONFIRM=AUTORIZADO`, `APP_BOOTSTRAP_GLOBAL_SCHEMA` correspondente ao banco conectado e nome/e-mail por `APP_BOOTSTRAP_GLOBAL_NOME` e `APP_BOOTSTRAP_GLOBAL_EMAIL`. Forneça a senha pelo terminal sem eco ou por entrada padrão protegida, nunca por argumento; ela deve ter 12–72 caracteres. Mantenha o bootstrap antigo de administrador de ONG desativado. O guard transacional rejeita novas execuções mesmo se a primeira conta estiver inativa. Antes de operação real revise identidade do servidor, privilégios, auditoria e recuperação; não execute este procedimento no principal sem autorização explícita.

Com essas variáveis e um `JWT_SECRET` válido já definidos em um ambiente isolado e terminal interativo, execute `java -jar build/libs/apisysanimais-0.1.0.jar`; o processo solicita a senha sem eco e encerra após o provisionamento. Para automação de teste, é possível alimentar a entrada padrão por canal protegido. A senha não deve aparecer em argumentos, arquivos versionados ou logs. Não configure uma porta separada de management durante o bootstrap. A comparação do esquema conectado é uma proteção adicional e não substitui verificar a identidade TLS do servidor de banco.

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

A migration V4 cria `animal` e `animal_foto`. Não inicie esta versão apontando para o banco principal antes de autorizar e planejar a aplicação da V4, mesmo com o Flyway desabilitado: a validação JPA exige tabelas ausentes e outras configurações podem reativar migrations. A imagem Docker atualmente implantada não contém este módulo.

A API obtém a ONG do JWT autenticado. Administrador e voluntário criam e alteram; veterinário também consulta; usuário público acessa somente o catálogo. Operações internas fora da ONG retornam 404; perfis insuficientes, 403. A listagem interna usa `page` (zero inicial), `size` (1–100; padrão 20) e `ativo` opcional. O cadastro começa ativo, não publicado e em tratamento. Inativação é lógica e idempotente; não há reativação.

Estados: `EM_TRATAMENTO` → `DISPONIVEL` ou `FALECIDO`; `DISPONIVEL` → `EM_TRATAMENTO`, `EM_PROCESSO_ADOCAO` ou `FALECIDO`; `EM_PROCESSO_ADOCAO` → `DISPONIVEL`, `EM_TRATAMENTO` ou `FALECIDO`. Repetir o mesmo estado é idempotente. `FALECIDO` e `ADOTADO` são terminais; não é possível marcar `ADOTADO` diretamente, pois falta o fluxo formal de adoção. Apadrinhamento será vínculo independente, não estado do animal. Situação e última pessoa/data da alteração são registradas; não existe histórico completo de transições.

`POST /api/animais` e `PUT /api/ong/animais/{id}` aceitam JSON com `nome`, `especie` (`CAO`, `GATO`, `OUTRO`), `sexo` (`MACHO`, `FEMEA`, `DESCONHECIDO`), `porte` (`PEQUENO`, `MEDIO`, `GRANDE`, `DESCONHECIDO`), `dataEntrada` (ISO 8601) e os opcionais `nascimentoEstimado`, `descricao`, `historia`, `dataResgate`, `origemResgate`, `observacoesResgate`. PUT substitui todos os campos editáveis e exige `versao`; inclua todos os campos que deseja preservar. ONG, situação e publicação são definidos pelo servidor. Consulte `GET /api/ong/animais[/{id}]`; use `PATCH /api/ong/animais/{id}/situacao` com situação e versão atual e `PATCH /api/ong/animais/{id}/inativacao` sem corpo.

`POST /api/ong/animais/{id}/fotos` recebe multipart `arquivo`, até 5 MiB, JPEG ou PNG válido, até 16 milhões de pixels e 10 fotos por animal. O servidor gera o nome, ignora o nome enviado e marca a primeira foto como principal. Os DTOs incluem URLs de leitura autenticada (`GET /api/ong/animais/{id}/fotos/{fotoId}`). O catálogo `GET /api/animais` só inclui registros ativos, disponíveis e publicados; fotos públicas usam `GET /api/animais/{id}/fotos/{fotoId}` com os mesmos critérios. Não há endpoint de publicação nesta fase.

Fotografias são arquivos locais, com metadados no MySQL; configure `APP_ANIMAL_PHOTOS_DIR` para diretório persistente e gravável pelo usuário 65534 do container, e monte um volume dedicado antes da implantação. Sem volume, arquivos serão perdidos na recriação. Backup/restauração deve abranger simultaneamente banco e diretório de fotos; interrupções de processo podem deixar arquivos órfãos, exigindo reconciliação operacional. Nunca monte diretórios de código ou arquivos sensíveis como armazenamento de fotos.

#### Controle de concorrência dos animais

As respostas internas de animal incluem `versao` (inteiro de 64 bits). Para `PUT /api/ong/animais/{id}`, envie todos os campos editáveis e a `versao` recebida; para `PATCH /api/ong/animais/{id}/situacao`, envie `{"situacao":"DISPONIVEL","versao":0}` com a versão atual. Versão ausente, negativa ou não inteira retorna 400. Uma versão anterior retorna 409 com `detail`: “Este animal foi atualizado por outra pessoa. Recarregue os dados antes de salvar novamente.” Em caso de 409 consulte novamente o animal e permita ao usuário revisar os dados; não reenvie automaticamente.

O Hibernate incrementa a versão após alterações do animal, inclusive situação, inativação e associação de foto. O upload não exige versão: é serializado pelo bloqueio do animal, que também preserva o limite de fotos e a consistência com inativação. Inativação continua idempotente e não exige versão. A versão retornada por uma operação de escrita é a versão após a gravação. A V4 já possui a coluna necessária; não há migration adicional.

O conflito de versão de animal retorna também a propriedade estável `code: "ANIMAL_VERSION_CONFLICT"` no `ProblemDetail` HTTP 409. Outros conflitos 409 não recebem esse código. O aplicativo deve identificar o conflito pelo código; se ele estiver ausente ou for desconhecido, deve apresentar conflito genérico sem reenviar a operação.

#### Recuperação de fotografias

O banco e os arquivos não compartilham uma transação ACID. Falhas normais após a gravação são compensadas quando o rollback é executado, mas um encerramento abrupto antes do commit pode deixar um arquivo sem metadado. Arquivo apagado, volume ausente ou restauração desencontrada pode deixar metadado sem arquivo; a leitura retorna 404 e registra aviso técnico sem caminho físico.

Para auditar, interrompa escritas de fotografias, preserve uma cópia privada do banco e do diretório, e compare em modo somente leitura os nomes gerados no diretório com `SELECT arquivo FROM animal_foto`. Trate separadamente arquivos sem referência e referências sem arquivo. Não apague automaticamente: confirme idade, cópias de segurança e eventuais transações em andamento; restaure ausentes de um backup compatível e investigue órfãos antes de qualquer remoção aprovada. O inventário e seus resultados podem conter identificadores internos e devem permanecer privados.

Para backup do MVP, interrompa escritas, aguarde transações terminarem, obtenha o dump consistente e copie o volume de fotos antes de retomar a API. Teste a restauração conjunta em ambiente isolado e compare referências antes de usar os dados restaurados. Cópias feitas em momentos independentes enquanto há uploads não garantem consistência. Configure `APP_ANIMAL_PHOTOS_DIR` em volume persistente gravável pelo usuário 65534 e proteja-o contra acesso de outros usuários do host.

Para uma auditoria offline reproduzível, interrompa **todos** os uploads e alterações nos metadados durante a exportação e a comparação. Exporte em TSV privado com um cliente MySQL configurado por um mecanismo seguro de credenciais (sem senhas na linha de comando):

```sql
SELECT f.id AS foto_id, f.animal_id AS animal_id, f.arquivo AS arquivo,
       CAST(f.principal AS UNSIGNED) AS principal,
       IF(a.id IS NULL, 0, 1) AS animal_existe
FROM animal_foto f LEFT JOIN animal a ON a.id = f.animal_id ORDER BY f.id;
```

Use o cliente com `--batch --raw` e cabeçalho, proteja o inventário e confira sua contagem contra o banco. Execute `python3 scripts/auditar_fotos.py --diretorio /diretorio/de/fotos --inventario /caminho/inventario.tsv --escritas-interrompidas`. O relatório JSON é somente leitura: detecta referências ausentes, órfãos, nomes inválidos, links simbólicos, animais inexistentes e anomalias de principal/quantidade; nunca remove ou repara itens. A opção é uma declaração operacional, não bloqueia a API nem comprova que as escritas pararam. Uma exportação incompleta ou dados alterados durante a auditoria podem produzir resultados incorretos. Proteja também o relatório; não use caminhos ou nomes registrados nele como comandos de exclusão.
