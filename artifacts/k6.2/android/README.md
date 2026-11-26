# Evidências Android reais — K6.2

Capturas por `adb exec-out screencap -p` no AVD Medium_Phone_API_37.0 host GPU, resolução 780×1688, density320 (390×844dp). Originais preservados; comparações reduzem Android uniformemente a 390×844, sem recortar ou esconder as barras nativas.

Fluxos realizados por toque/teclado no APK, backend Kotlin 18082 → MySQL 8.4 isolado patas-k61-isolado/13317:

- Login donor_a; catálogo público Tito (animal1, ONG6101); registro de intenção com `36,15`; sucesso pendente; Minhas doações mostra `R$ 36,15` e não pagamento.
- Logout/login admin_a; Doações recebidas → doação6 → confirmação inline de recebimento → CONFIRMADA. Receita automática12 REALIZADA, criada pelo servidor. Receitas oficiais36,45 →72,60, diferença36,15; saldo29,10→65,25.
- Novo lançamento DESPESA/ALIMENTACAO/`7,25`/K62_UI_racao → movimento13 PENDENTE. Aprovação com confirmação inline →APROVADA, despesasAprovadas7,25 e saldoinalterado65,25. Confirmação de pagamento distinta →REALIZADA. Despesas realizadas7,35→14,60, saldo65,25→58,00. Histórico preserva os três eventos.
- Novo lançamento RECEITA/EVENTO/`12,34`/K62_UI_receita_cancelar →movimento14 PENDENTE. Cancelamento com justificativa opcional K62_UI_validacao_cancelamento e confirmação →CANCELADA. Histórico preservado, saldo58,00 e receitasPendentes700,30 anteriores mantidos.
- Logout/login volunteer; consultou receitaPENDENTE7; painel Próximo passo sem aprovar/receber/pagar/cancelar (17-voluntario-sem-decisoes.png).

Snapshots HTTP apenas de leitura após ações UI em `../ui-before-confirm.json`, `../ui-after-confirm.json`, `../ui-after-expense-create.json`, `../ui-after-expense-approve.json`, `../ui-after-expense-paid.json`, `../ui-after-cancel.json`. Incluem IDs, estados, histórico e resumo oficial. Não contêm senhas/tokens. Outros testes automatizados do processo principal podem adicionar registros depois destes snapshots; os deltas acima são os comprovados durante a sequência UI isolada.

Os arquivos 02c e07b comprovam teclado em formulário público e financeiro; 09b,12b,13 e15 comprovam confirmações inline;14 e16 comprovam histórico após transição. A versão Web não tem confirmação institucional, portanto comparações09/12 usam os estados de pré-ação reais; a confirmação Android adicional atende o requisito de segurança K6.2. Não se inventou diálogo React.

Recapturas finais read-only:01,02,02b,03,04,05,06,07,08,10,11 com APK refinado. `18-regressao-adocoes.png`: login donor_a, catálogo, animal e doações anteriores navegáveis; Minhas adoções mostra vazio legítimo e permite voltar ao catálogo. Logout/login admin_a repetidos com sucesso. `logcat-crash.txt` vazio após todos os fluxos/capturas (nenhum crash registrado no buffer). Última leitura oficial `../ui-final-readonly.json`:73,20/14,80/saldo58,40. A diferença de +0,30/+0,10 após nosso fluxoUI provém da suíte real final executada pelo processo principal.
