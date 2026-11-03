# API de cadastro de usuários

API Kotlin/Spring Boot para o cadastro de interessados, apadrinhadores e equipe de ONGs. Esta etapa não inclui cadastro de ONGs, animais ou frontend. A API usa MySQL, BCrypt, JWT e migrations Flyway.

## Banco e execução

Pode usar um **banco vazio** ou um banco já criado pelo script acadêmico `mysql_ong_scrpit.sql`. Nesse segundo caso, não execute o script novamente: ele começa com `DROP DATABASE`. Ao iniciar, o Flyway marca o esquema existente como versão 1 e a migration V2 altera `usuario.ong_id` para aceitar contas externas e amplia `perfil`. O restante das tabelas do script permanece intacto.

```sql
CREATE DATABASE gestor_adocao CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Defina `DB_URL` (JDBC MySQL), `DB_USER`, `DB_PASSWORD` e `JWT_SECRET` (ao menos 32 bytes):

```bash
export DB_URL='jdbc:mysql://localhost:3306/gestor_adocao'
export DB_USER='root'
export DB_PASSWORD='sua-senha-mysql'
export JWT_SECRET='troque-isto-por-uma-chave-aleatoria-de-32-bytes-ou-mais'
./gradlew bootRun
```

Em banco vazio, essa primeira inicialização, **sem** `BOOTSTRAP_ADMIN_*`, cria as tabelas. Pare a API e crie uma ONG temporária pelo MySQL. Se o banco já veio do script acadêmico, as ONGs de exemplo já existem; use o ID de uma delas. Depois defina as variáveis de bootstrap para a próxima inicialização:

```sql
INSERT INTO ong (nome, email, cidade, estado)
VALUES ('Patas Amigas', 'contato@patasamigas.org', 'Manaus', 'AM');
```

```bash
export BOOTSTRAP_ADMIN_ONG_ID='1'
export BOOTSTRAP_ADMIN_NAME='Administrador inicial'
export BOOTSTRAP_ADMIN_EMAIL='admin@patasamigas.org'
export BOOTSTRAP_ADMIN_PASSWORD='uma-senha-forte-de-12-ou-mais-caracteres'
./gradlew bootRun
```

Depois de confirmar que o administrador foi criado, remova as variáveis `BOOTSTRAP_ADMIN_*` das próximas inicializações. Nunca grave senhas ou a chave JWT no repositório. A criação da ONG via API será feita na etapa seguinte.

## Contrato HTTP

`POST /api/usuarios/cadastro` é público. Envie `nome`, `email`, `senha` e, opcionalmente, `tipo` (`INTERESSADO`, padrão, ou `APADRINHADOR`). O servidor não aceita `ongId` nem `perfil` nessa rota.

```json
{"nome":"Ana Lima","email":"ana@exemplo.com","senha":"SenhaForte123!","tipo":"INTERESSADO"}
```

`POST /api/auth/login` recebe `email` e `senha` e devolve `accessToken` válido por uma hora.

```json
{"email":"admin@patasamigas.org","senha":"uma-senha-forte-de-12-ou-mais-caracteres"}
```

`POST /api/usuarios/internos` exige `Authorization: Bearer <accessToken>` de um `ADMINISTRADOR`. Envie `nome`, `email`, `senha` e `perfil` (`ADMINISTRADOR`, `VOLUNTARIO` ou `VETERINARIO`). A ONG é obtida da conta autenticada, nunca do corpo da requisição.

```json
{"nome":"Bia Rocha","email":"bia@patasamigas.org","senha":"SenhaForte123!","perfil":"VOLUNTARIO"}
```

Cadastros válidos retornam `201` com `id`, `nome`, `email`, `perfil` e `ongId` (nulo em contas públicas). Dados inválidos retornam `400`, e-mail já usado `409`, ausência de autenticação `401` e falta de permissão `403`. A senha e seu hash nunca são retornados.

## Testes

```bash
./gradlew test
```

Os testes usam H2 em modo MySQL. Para verificar a integração com o MySQL real, execute a API com as variáveis acima e envie os três tipos de requisição a partir de Postman, Insomnia ou `curl`.
