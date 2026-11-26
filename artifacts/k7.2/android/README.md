# Android real K7.2

Capturas ADB no AVD Medium_Phone_API_37.0, GPUhost,2CPUs/memória2048,780×1688/density320. APIKotlin18083, MySQL8.4 isolado patas-k71-isolado/patas_k71, V10. Dados exclusivamente sintéticos.

01–13: registro de consulta e correção/histórico reais pelo veterinário. Registro10 do animal2, autor7103, versão0→1; observação removida por null, título alterado, justificativa preservada. 03 registra teclado e04/05 rolagem até a ação.

14–18: primeiras recapturas e erro de formulário vazio inicialmente fora da viewport, usado para reproduzir a falha visual.

19–21: voluntário consulta lista/detalhe/histórico, sem Novo/Corrigir.

22–26: solicitação de adoção2 pelo usuário, aprovação administrativa e entrega confirmada viaUI. Screenshot24 APROVADA sem acesso clínico;26 CONCLUIDA com link veterinário. `access-approved-before-delivery.jsonl` confirma404 antes da entrega; `access-after-delivery.jsonl` confirma200 somente ao titular e404 a outro usuário.

27/28: consulta pessoal inicial.29–31: conflito real com alteração administrativa em outra sessão, rascunho mantido, revisão e reenvio manual. Metadados antes do novo envio ainda mostram versão2; após novoSalvar,3. Nenhuma repetição automática de escrita. Autor original7103 preservado. Mudança administrativa de profissional e título local foram ambos mantidos.

32–40: validação de formulário vazio corrigida (33 mostra erro junto à ação), listas/detalhes/histórico e projeção pessoal. Personal-ui-projection.json verifica ausência de ações institucionais/rastreabilidade/versão. O animal3 não recebeu qualquer prontuário pelo formulário inválido.

41/42: navegação anterior Financeiro e catálogo público, sem escrita financeira.

43–49: recapturas de topo do último APK, com títulos no tracking oficial CSS; são as principais fontes finais da comparação. 43 listaLuaADOTADO semNovo;44detalhe;45correção;46histórico;47formnovoMel;48timelinepessoal;49detalhepessoal.

Logs/JSON nesta pasta incluem apenas metadados de acesso, IDs, versões e flags; nenhum JWT/senha/corpo clínico completo. Os screenshots contêm os dados sintéticos solicitados para avaliação visual. Buffer logcat de crashes vazio no fluxo exercitado.
