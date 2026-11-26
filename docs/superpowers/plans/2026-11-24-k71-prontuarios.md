# K7.1 — plano de implementação

Objetivo: prontuários específicos com a estrutura Controller → Service → Repository já autorizada no pedido K7.1. Sem Android, Web, dependências, commits ou migrations históricas alteradas.

Design: uma entidade de registro clínico genérico, oito tipos reais Laravel (incluindo os seis exigidos), e uma entidade de correção com snapshots antes/depois, justificativa, ator, data e versões. Sem exclusão/edição comum. V10 cria tabelas e chave composta animal/ONG. Reutilizar @Version, locks e paginação Kotlin, com versão obrigatória na correção; evitar seis tabelas por tipo e infraestrutura genérica de auditoria.

1. Escrever ProntuarioApiTests via MockMvc com fixtures transacionais H2/Flyway: tipos, validação, permissões, animal/ONG, estados, filtros, correção auditada e versões, adotante antes/depois da entrega. Executar RED sem os endpoints.
2. Criar prontuarios/{domain,dto,repository,service,controller}, V10 e regras Spring Security antes dos matchers genéricos de animais. Registrar ator/ONG da sessão; consulta adotante exige adocao + solicitacao CONCLUIDA coerentes; resposta adotante reduzida. Correção parcial distingue ausente/null, exige justificativa e mudança real, mantém autor original. Snapshots serializados em TEXT com Jackson existente.
3. Executar testes direcionados até GREEN; build e suíte completa. Testar rollback de correção e FKs. Revisar alterações limitadas.
4. MySQL 8.4 em container separado K7.1: aplicar V1–V10, iniciar JAR atual porta própria com validate; fluxos HTTP de registro/correção/histórico/adoção/entrega/acesso indevido e checks de banco. Não reutilizar dados demonstrativos.
5. Documentar contratos JSON, sete endpoints, perfis, divergências Kotlin/Laravel, resultados reais, arquivos e limites em docs/k7.1-prontuarios.md. Conferir hashes Android/Web/migrations V1–V9 e encerrar K7.1 sem avançar K7.2.

Revisão focal: campos internos injetados; correção concorrente/desatualizada; ausência versus null; adoção APROVADA versus CONCLUIDA; registro de outro animal/ONG e projeção adotante sem histórico administrativo. Cobertura proporcional por testes do módulo e fluxo real MySQL.
