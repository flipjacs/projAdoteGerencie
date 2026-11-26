# Referência React real — K6.2

Capturas Playwright reais em viewport 390 × 844, 24/11/2026 UTC. Frontend `http://127.0.0.1:5174`, API Laravel `http://127.0.0.1:8001`. Contas demo autorizadas, consultas e login/logout apenas; nenhum cadastro ou transição financeira executada no Web.

## Capturas e rotas

- `01-area-doacoes.png`: `/animais/36`, área real de apoio à ONG no detalhe do animal, rolada até as ações.
- `02-formulario-doacao.png`: `/animais/36/doar`, formulário público contextualizado pela ONG do animal.
- `02b-formulario-doacao-campos.png`: mesma rota, campos de valor/mensagem (viewport).
- `03-minhas-doacoes.png`: `/minha-area/doacoes`, registros existentes confirmados/cancelados do próprio usuário.
- `04-dashboard-financeiro.png`: `/ong/financeiro`, seis indicadores oficiais em grid de duas colunas e destaque verde para resultado.
- `05-movimentacoes.png`: mesma rota, histórico responsivo de cartões com rótulos à esquerda e dados à direita.
- `06-formulario-receita.png`: `/ong/financeiro/novo`, tipo Receita, categoria Evento, valor, descrição e Registrar pendência.
- `07-formulario-despesa.png`: mesma rota, tipo Despesa; categorias estruturadas pertencem ao Web K11 e não devem ampliar escopo Kotlin K6.2.
- `08-detalhes-despesa.png`: `/ong/financeiro/47`, despesa pendente existente.
- `09-aprovacao-preacao.png`: mesma rota, painel Próximo passo e botão Aprovar despesa, sem acionamento.
- `10-doacoes-recebidas.png`: `/ong/doacoes`, filtros de situação/datas e registros existentes.
- `11-detalhes-doacao.png`: `/ong/doacoes/12`, recebimento confirmado existente, dados e histórico.
- `12-pagamento-preacao.png`: `/ong/financeiro/30`, despesa aprovada existente; botão Confirmar pagamento, sem acionamento.

## Referências examinadas

Base `/home/flipjacs/IdeaProjects/PatasUnidasWeb/frontend/src/`:

- `features/institutional/Finance.tsx`: FinanceOverview, FinanceCreate, FinanceDetails.
- `features/institutional/FinancialRelations.tsx`: DonationList, DonationDetails.
- `features/institutional/InstitutionalUi.tsx`: PageHeading, Status, Empty, LoadState, Pager.
- `features/institutional/finance.css`: indicadores 3 colunas desktop, 2 abaixo de 800px; cartões 0.8rem; valores 1.35rem/800.
- `features/institutional/financialFormat.ts`: moeda/data.
- `features/personal/PersonalActions.tsx`: intenção de doação, valor aceita vírgula/ponto; aviso de não pagamento; sucesso pendente.
- `features/personal/PersonalLists.tsx`: próprias doações e confirmação inline de cancelamento quando pendente.
- `features/personal/PersonalUi.tsx`: ConfirmAction, Status e estados compartilhados.
- `styles/index.css`: Manrope Variable; forest #173D2B, foliage #32664B, sand #F6F1E6, paper #FFFDFA.
- `styles/institutional.css`: mobile 16px margem, cabeçalho institucional com menu/logotipo/sair; formulário uma coluna <=640px; tabela vira cartões; facts em linhas.
- `styles/personal.css`: formulário e contexto branco, bordas suaves; heading 32px mobile, valores 25.6px; chips semânticos.
- `styles/workspace.css`, `app/App.tsx`: frames e rotas reais.

## Estados e limites comprovados

FinanceDetails e DonationDetails institucionais NÃO abrem diálogo de confirmação: `onClick` chama `decide` diretamente e faz requisição de transição. Por isso os registros 09 e 12 são estados de pré-ação, não diálogos inventados. A confirmação inline existe em PersonalLists para cancelamento de doação pendente; a conta demo consultada possui somente confirmadas/canceladas e nenhum registro foi criado para forçar este estado. Carregamento, erro/retry e vazio existem nos componentes reais; capturas principais mostram os dados de demonstração existentes. Estoque, categorias estruturadas e análise avançada visíveis no Web pertencem a K11 e não são requisitos de implementação K6.2.

AVD preparado: Medium_Phone_API_37.0 com host GPU, Vulkan desabilitado, sem janela/áudio/snapshot/câmeras. `emulator-5554`, resolução 780×1688 e density320 (390×844dp). Java Kotlin PID404969 porta18082 com health200, MySQL patas-k61-isolado porta13317. Processos Web mantidos: Vite PID432141 (sessão49888), Laravel PID432475 (sessão69078); AVD PID432203 (sessão58454). Credenciais nunca incluídas nos artefatos.
