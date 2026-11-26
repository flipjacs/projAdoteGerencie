# Comparação visual real — K6.2

13 PNGs: Web à esquerda (original390×844) e Android à direita (original780×1688 reduzido uniformemente50%, equivalente390×844dp). Rótulos identificam as versões. As capturas originais permanecem nas pastas web/android. Não houve mockups ou alterações das interfaces fotografadas.

Inspeção visual efetuada nas13comparações em prancha e em resolução legível nos formulários02/06 e dashboard04; inspeção adicional dos originais do teclado07b. Os estados de doação e valores variam porque React usa seu banco demonstrativo preservado e Android usa MySQLKotlin isolado.

## Ajustes comprovados e efetuados

- Título de intenção de doação alinhado a “Registrar uma doação”.
- Margens financeiras20→16dp, título30→32sp.
- “Voltar” retirado da visão geral, mantido em detalhe/formulário, como na referência.
- Indicadores compactados de min110→90dp; duas colunas e resultado em verde preservados.
- Abas internas deslocadas para depois dos indicadores no dashboard, priorizando as seis medidas.
- Novo lançamento, lista responsiva com rótulos, doações próprias/contexto da ONG, painel Próximo passo, chips de estado e fatos financeiros preservam a hierarquia React.

As imagens01,02,02b,03,04,05,06,07,08,10,11 são do APK final refinado; 09 e12 registram os estados transitórios PENDENTE/APROVADA durante o fluxo real na primeira instalação da K6.2, antes dos ajustes de espaçamento, pois os registros já avançaram no servidor e nenhum estado foi revertido ou simulado para refazer capturas. As confirmações09b/12b/13/15 são evidências adicionais Android reais.

## Diferenças remanescentes justificadas

- Barras nativas de status/gestos, menu Android de retorno, teclado e dropdowns seguem a plataforma; permanecem visíveis nas comparações.
- A navegação pública Android existente usa Início/Animais/Doações e acesso às adoções; a React também expõe apadrinhamentos/adotados, foraK6.2. A navegação global não foi reconstruída.
- Corpo de texto e alguns controles Android são mais altos; formulários permanecem roláveis, com teclado decimal inspecionado e botão acessível por rolagem. O formulário financeiro aceita vírgula/ponto e exibe reais, adaptando entrada local para o decimal string estrito Kotlin.
- Cabeçalhos e campos factuais de detalhe não têm igualdade pixel a pixel: Kotlin oferece histórico explícito, justificativa opcional e detalhe pessoal adicional; o histórico acrescenta altura. Screenshot08Web é despesa pendente;08Androidfinal é despesa realizada16, mostrando atores/datas já preenchidos. A aprovação e o pagamento intermediários estão em09/12.
- Android exige confirmação inline antes de decisões institucionais para atender ao enunciado; React chama diretamente a API nessas ações. As comparações09/12 mostram pré-ação real React, sem inventar um diálogo Web.
- Categorias estruturadas, análise avançada e estoque da referência atual Web pertencemK11; Android mantém categorias estáticas reaisK6.1 e não incluiu esses módulos.
- Dadosnomes/valores/status e alguns rótulos de ausência diferem conforme os contratos/ambientes. Último resumoAndroidoficial73,20 receitas/14,80 despesas/58,40 saldo, após os testes finais automatizados acrescentarem0,30/0,10 aos deltasUI registrados no READMEAndroid.

A equivalência é reconhecível por composição/hierarquia, e não é uma alegação de igualdade pixel a pixel. Nenhum gráfico ou tela financeira genérica substituiu a referência.

Verificação final da cor selecionada: chips Movimentações/Doações agora verdes após comparação revelar o lilás padrãoMaterial. As capturas04/10 foram refeitas no último APK e as13comparações regeneradas. Logcat crash vazio.
