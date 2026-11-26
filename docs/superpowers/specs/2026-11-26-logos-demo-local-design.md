# Logos locais para a demonstração Patas Unidas

## Intenção

Adicionar à demonstração Android logos de organizações de proteção animal reais obtidos de fontes abertas, mantendo as duas ONGs fictícias do banco claramente identificadas como demonstração sem vínculo com as organizações representadas. As fotos reais dos sete animais já foram aplicadas à base MySQL isolada.

## Objetivos

- Exibir um logo local associado às ONGs demonstrativas de IDs 1 e 2.
- Informar junto à identificação visual que cada logo é apenas referência e não indica afiliação.
- Registrar fonte, autoria e licença de cada arquivo.
- Não exigir acesso de rede para exibir logos na demonstração.

## Fora do escopo

- Alterar API, banco de dados, backend Kotlin ou frontend Web.
- Adicionar campos ou endpoints de logo.
- Criar imagens por geração artificial.
- Apresentar os logos como marcas oficiais das ONGs fictícias.

## Abordagens consideradas

1. **Integração pela API:** adicionaria contratos e armazenamento de logos reutilizáveis, porém amplia o escopo e exige alterações no backend e nas telas.
2. **Arquivos locais no Android:** associa dois recursos visuais aos IDs existentes; simples e offline, adequada à demonstração solicitada.
3. **Sem logos:** evita associação visual, mas deixa de atender ao pedido.

## Abordagem escolhida

Arquivos locais no Android, escolhidos pelo ID da ONG, com créditos/licenças em `artifacts/demo/logos/LEIA-ME.md` e aviso visível “Logo de referência; ONG demonstrativa sem vínculo com a organização”. O usuário escolheu essa solução local e simples. Usar somente logos com licença aberta verificável e atribuição documentada.

## Aplicação visual

- Exibir o logo ao lado do nome nas linhas/cards da listagem institucional e no detalhe da ONG.
- Exibir o mesmo logo junto ao bloco “ONG responsável” no detalhe público do animal, quando o ID corresponder a uma ONG demonstrativa.
- Para IDs sem mapeamento, manter o conteúdo visual atual.
- Manter o aviso próximo ao logo, com contraste e tamanho legíveis.

## Validação

Confirmar carregamento dos dois recursos no build Android, conferir a associação de IDs e revisar as telas afetadas no APK de demonstração. Não alterar nem reenviar imagens de animais já carregadas.
