# Patas Unidas Web

Base independente para a futura versão web do Patas Unidas. Ainda não há telas nem funcionalidades de cadastro ou autenticação.

## Organização

- `frontend/`: React, TypeScript e Vite; `src/features` para funcionalidades e `src/shared` para componentes e integração REST. `src/app` contém a composição da aplicação; `src/styles` centraliza estilos.
- `backend/`: Laravel convencional, com rotas REST preparadas em `routes/api.php`. Controllers, Form Requests, models, middleware e policies devem seguir as convenções do Laravel; adicione services apenas quando necessários. Sanctum está instalado, mas fluxos de autenticação, middleware stateful e migração de tokens ainda não estão configurados.
- `docker-compose.yml`: apenas MySQL local, com volume e rede próprios. PHP e Vite executam no host.

## Requisitos

Docker e Docker Compose; PHP 8.3 a 8.5 com extensões exigidas pelo Laravel, incluindo PDO MySQL; Composer; Node.js 22.12+ e npm. As versões exatas estão nos arquivos de lock. Exemplos de credenciais são exclusivos para desenvolvimento local.

## Primeira execução

Na raiz do projeto:

```sh
cp .env.example .env
cp backend/.env.example backend/.env
cp frontend/.env.example frontend/.env
# Altere as senhas locais e mantenha MYSQL_PASSWORD e DB_PASSWORD iguais.
docker compose up -d --wait
cd backend
composer install
php artisan key:generate
```

Em outro terminal, para o backend:

```sh
cd backend
php artisan serve --host=127.0.0.1 --port=8002
```

Em outro terminal, para o frontend:

```sh
cd frontend
npm ci
npm run dev -- --host 127.0.0.1 --port 5175 --strictPort
```

Acesse `http://127.0.0.1:5175`. A API ficará em `http://127.0.0.1:8002/api`; `VITE_API_BASE_URL` é um ponto de configuração para futura integração REST. A página permanece vazia intencionalmente. O endpoint de saúde do Laravel é `http://127.0.0.1:8002/up`.

O MySQL fica em `127.0.0.1:3310` por padrão, com banco e usuário próprios. A alteração de credenciais no `.env` do Compose após a criação do volume não altera usuários já inicializados no MySQL. Para parar sem apagar os dados: `docker compose down`. Não use as credenciais de exemplo em produção.

## Validação

```sh
cd frontend && npm run build && npm run lint
cd ../backend && php artisan test
```

## Pendências

Definir contratos e funcionalidades a partir da referência Android em etapa futura; configurar CORS, cookies e autenticação Sanctum conforme o fluxo escolhido; implementar integração REST e telas. A estrutura inicial não usa o banco nem o código do projeto Android. Nenhuma migration é executada automaticamente; as migrations padrão do Laravel ficam pendentes até serem necessárias.
