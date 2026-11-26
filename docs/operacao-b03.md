# Operação segura do backend

Este roteiro prepara uma implantação futura; não autoriza nem executa alterações no ambiente principal. O Flyway da imagem antiga implantada possui configuração própria. O código atual deixa Flyway desativado por padrão e valida o esquema com Hibernate. Isso não verifica todas as migrations pendentes: execute validação Flyway separada antes de iniciar a aplicação.

## Ambientes e credenciais

- Compile com JDK 21: `JAVA_HOME=/caminho/jdk21 ./gradlew test bootJar`. Os testes Gradle ativam Flyway explicitamente somente nos bancos de teste configurados neles.
- O exemplo `compose.mysql-validation.yaml` usa MySQL isolado na porta local 3309. Não a confunda com 3308 do MySQL principal nem com 8080 da API principal; o exemplo de API de teste usa 8081.
- Em implantação futura use `SPRING_PROFILES_ACTIVE=prod`, `DB_URL`, `DB_USER`, `DB_PASSWORD` e `JWT_SECRET` fornecidos de forma protegida. O perfil exige as três variáveis de banco; o segredo JWT precisa de pelo menos 32 bytes. O perfil não valida se a URL JDBC usa TLS: exija `sslMode=VERIFY_IDENTITY`, cadeia confiável e nome do servidor compatível antes da liberação. Não use o fallback `root` ou JDBC sem TLS do perfil local.
- Mantenha `.env` fora do Git e legível somente pelo responsável (`chmod 600`); não coloque segredos em comandos, relatórios, imagens ou logs. O bootstrap administrativo deve permanecer desativado após sua utilização autorizada.
- Conta de execução: conceda apenas SELECT, INSERT, UPDATE e DELETE nas tabelas necessárias do esquema, sem DDL nem privilégios globais; revise a lista à medida que os módulos evoluírem. Para validação Flyway, conceda leitura da tabela de histórico. Conta de migração separada e temporária recebe os privilégios DDL específicos necessários às migrations revisadas, incluindo criação/alteração de tabelas, índices e chaves estrangeiras; remova-os depois. Conta de backup precisa somente das permissões necessárias ao método de dump adotado, validadas no ambiente.

## Migrations e implantação futura

1. Identifique container, servidor, banco, versão e histórico Flyway sem divulgar credenciais; confirme explicitamente o destino. V4 já está validada apenas em isolamento. Não altere nem renomeie V1–V4. Migrations de B1 podem iniciar em V5, mesmo que o módulo de animais seja consolidado depois.
2. Suspenda escritas, confirme término de transações, faça e valide backup coordenado conforme abaixo. Revise a V4 e todas as migrations pendentes. Não use `flyway clean`.
3. Em isolamento, execute a aplicação de migrations com credencial de migração exclusiva e `SPRING_FLYWAY_ENABLED=true`, após confirmar a URL; confira histórico, integridade e restauração. A opção faz a aplicação executar **todas** as migrations pendentes encontradas, não apenas V4.
4. Somente após autorização específica, aplique o procedimento aprovado ao destino. Retire a credencial de migração, inicie API com Flyway desativado e credencial sem DDL. Confira histórico, healthcheck, autenticação e autorização. Uma configuração explícita pode sobrepor `enabled: false`; a separação de privilégios é proteção adicional indispensável.
5. Mantenha imagem anterior identificada. Reverter imagem não reverte esquema nem dados; teste previamente compatibilidade. Qualquer restauração de dados deve ser uma decisão explícita, considerando perda de escritas posteriores e coerência com as fotos.

## Fotografias e backups

Use diretório persistente dedicado em `APP_ANIMAL_PHOTOS_DIR`, não servido estaticamente, gravável pelo UID/GID 65534 e inacessível a outros usuários não autorizados. Prepare-o fora de diretórios de código ou credenciais. [O arquivo de exemplo](../compose.fotos.example.yaml) apenas descreve um bind mount: não o aplique ao Compose principal sem revisar caminhos, rótulos SELinux, permissões, rede e impacto. `ANIMAL_PHOTOS_HOST_DIR` deve apontar para diretório já existente e privado. Proteja também ancestrais contra substituição e garanta capacidade e monitoramento.

Para o MVP, suspenda todas as escritas no banco e em fotos e aguarde transações e uploads terminarem. Gere um dump consistente com `mysqldump --single-transaction --no-tablespaces` usando credenciais protegidas, copie o diretório de fotos durante a mesma janela, registre os checksums dos artefatos e a identificação de imagem, configurações sem segredos e migrations. Restrinja os arquivos de backup; guarde-os fora do host e teste retenção e restauração. A suspensão de escritas deve continuar até as duas cópias terminarem; um dump transacional isolado não sincroniza o sistema de arquivos.

Restaure o dump em banco isolado novo e fotos em diretório isolado. Compare checksums, histórico Flyway, contagens e integridade referencial. Execute login com conta sintética e leitura autorizada no ambiente restaurado. Gere o TSV privado por consulta documentada no README, confira sua contagem, e execute `python3 scripts/auditar_fotos.py --diretorio DIRETORIO --inventario INVENTARIO --escritas-interrompidas`. Essa flag é declaração humana, não bloqueio. Confirme que não há ausentes, órfãos, links ou anomalias de principal; trate achados manualmente. Não apague órfãos automaticamente. Para arquivo ausente, procure backup consistente; para órfão, investigue transações, histórico e backups antes de qualquer exclusão autorizada. Uma queda entre escrita e commit ainda pode produzir órfãos.

## Exposição e diagnóstico

A API atual pode escutar em todas as interfaces; não infira exposição pública sem verificar rede e firewall. Prefira bind local ou rede privada e TLS terminado em proxy confiável. Não confie em `X-Forwarded-For` recebido diretamente: configure uma cadeia de proxies explicitamente confiável antes de usar IP encaminhado para limites. O limitador atual é por instância, perde estado em reinício, afeta usuários sob NAT e não substitui proteção compartilhada na borda.

`GET /actuator/health` deve retornar somente estado básico; outros endpoints de diagnóstico não devem ser publicados. Use `docker inspect` para estado, `RestartCount`, `StartedAt`, saúde e limites; consulte logs de forma restrita e sem divulgar dados pessoais. O Hibernate pode registrar detalhes de violações SQL, inclusive e-mail duplicado: restrinja acesso e retenção de logs até uma correção testada. Não imprima variáveis de ambiente. Status `healthy` não comprova recuperação, segurança ou consistência dos dados.
