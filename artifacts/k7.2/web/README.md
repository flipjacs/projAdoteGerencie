# Referência Web K7.2

Capturas reais pelo Playwright MCP, viewport CSS 390 × 844, 24/11/2026. Frontend React em http://127.0.0.1:5174 e API Laravel em http://127.0.0.1:8001. Dados demonstrativos preexistentes. Somente autenticação e consultas: nenhum formulário submetido, nenhum prontuário criado/corrigido, nenhuma adoção alterada. Fontes Web preservadas.

`*-top.png` e `*-lower.png` preservam a viewport; `*-full.png` documenta a página inteira e não deve ser comparado com uma única viewport Android. As capturas originais permanecem intactas.

| Prefixo | Rota | Evidência |
| --- | --- | --- |
| 01-animal-entry | /ong/prontuarios | Seleção institucional de animal |
| 02-institutional-list | /ong/animais/34/prontuarios | Lista, filtros e Novo registro |
| 03-create | /ong/animais/34/prontuarios/novo | Formulário vazio, topo e botões inferiores |
| 04-institutional-detail | /ong/animais/28/prontuarios/6 | Detalhe clínico e rastreabilidade |
| 05-correction | /ong/animais/28/prontuarios/6/corrigir | Formulário preenchido e justificativa |
| 06-correction-history | /ong/animais/28/prontuarios/6/correcoes | Correção real preexistente, Antes e Depois |
| 07-adopter-entry | /minha-area/adotados | Animais com adoções concluídas |
| 08-adopter-history | /minha-area/adotados/28/prontuarios | Timeline clínica somente leitura |
| 09-adopter-detail | /minha-area/adotados/28/prontuarios/6 | Projeção pessoal reduzida |

## Fontes consultadas diretamente

Base `/home/flipjacs/IdeaProjects/PatasUnidasWeb/frontend/src/`:

- `features/institutional/MedicalRecords.tsx`: seleção, lista, formulário, detalhe, correção e versões.
- `features/institutional/api.ts`: tipos e operações React; os contratos Laravel servem apenas à referência Web, não substituem Kotlin.
- `features/institutional/InstitutionalUi.tsx`: cabeçalho, paginação, status, vazio, carregamento e erro.
- `features/personal/AdoptedPages.tsx`: animais concluídos, timeline e detalhe reduzido.
- `features/personal/PersonalLists.tsx`: entrada para histórico apenas em adoção concluída.
- `features/personal/api.ts`: projeção clínica pessoal e endpoints de leitura.
- `styles/index.css`: Manrope Variable e tokens globais Amazônia Contemporânea.
- `styles/institutional.css`: componentes e regras responsivas clínicas.
- `styles/personal.css`: timeline e detalhe pessoal.
- `styles/workspace.css`: shell institucional e pessoal em mobile.
- `shared/api/client.ts`, `features/auth/auth.ts`, `features/auth/AuthContext.tsx`: autenticação real para captura.

## Padrões observados em 390px

- Margens externas de 16px, fundo areia #f6f1e6, superfícies paper #fffdfa, borda #d9e0d6, texto verde-floresta #173d2b e secundário #576b5d; Manrope.
- Cabeçalho institucional 32px/1.15, peso750; link Voltar acima; ação principal em linha própria e largura total.
- Filtros Tipo, Data inicial e Data final em uma coluna a 390px. Não ficam em três colunas.
- Formulário em superfície com raio16px, padding20px. Campos em coluna única, labels12.8px/peso750, gap7.2px. Inputs com altura mínima44.8px, raio9.6px e fundo branco; espaço entre campos19.2px.
- Ordem: Tipo, Data do procedimento, Título, Descrição clínica, Profissional responsável (opcional), Medicamentos (opcional), Observações (opcional); na correção, Justificativa da correção ao final.
- Card de registro: tipo em pílula suave e data no topo, título18.4px, descrição, autor e Abrir registro.
- Detalhe: subtítulo tipo/data, seção clínica com fatos empilhados e divisórias; Rastreabilidade separada abaixo no mobile.
- Histórico: um card por correção; data, responsável e justificativa; painéis Antes e Depois empilhados (nunca lado a lado em 390px), cada um com borda, raio11.2px, padding16px. Cada versão contém labels e valores em uma coluna.
- Adotante: eyebrow Animais adotados, título Histórico veterinário, aviso de consulta somente leitura; timeline com linha esquerda e data acima do registro. Detalhe pessoal tem descrição e somente campos opcionais presentes; sem rastreabilidade, versão, correções ou escrita.

## Diferenças contratuais que exigem adaptação Android

- Kotlin exige versão na correção e entrega versão anterior/posterior; React/Laravel consultado usa updatedAt para detectar conflito. Android deve seguir Kotlin.
- Paginação Laravel inicia em1 e usa data/meta; Kotlin inicia em0 e usa content/number/size/totalElements/totalPages.
- O exemplo de histórico Web tem campos opcionais ausentes; não inventar valores para preencher painéis.
- Dados clínicos e nomes demonstrativos Web diferem dos dados sintéticos Kotlin. A equivalência visual deve avaliar componentes, hierarquia e fluxo, não igualdade do texto.
