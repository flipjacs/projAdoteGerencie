ALTER TABLE usuario ADD COLUMN credencial_temporaria_expira_em TIMESTAMP(6) NULL;
ALTER TABLE usuario ADD COLUMN provisionamento_virgem BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE usuario ADD COLUMN primeiro_acesso_concluido BOOLEAN NOT NULL DEFAULT TRUE;
UPDATE usuario u SET u.provisionamento_virgem = TRUE
 WHERE u.ativo = FALSE AND u.credencial_pendente = TRUE
 AND (EXISTS (SELECT 1 FROM auditoria_equipe a WHERE a.funcionario_id=u.id AND a.operacao='FUNCIONARIO_CRIADO')
   OR EXISTS (SELECT 1 FROM auditoria_institucional a WHERE a.usuario_alvo_id=u.id AND a.operacao='ADMIN_INICIAL_PROVISIONADO'))
 AND NOT EXISTS (SELECT 1 FROM auditoria_equipe a WHERE a.funcionario_id=u.id AND a.operacao='FUNCIONARIO_DESATIVADO');
UPDATE usuario u SET u.primeiro_acesso_concluido = FALSE
 WHERE u.credencial_pendente=TRUE AND EXISTS (SELECT 1 FROM auditoria_institucional a WHERE a.usuario_alvo_id=u.id AND a.operacao='ADMIN_INICIAL_PROVISIONADO');
ALTER TABLE usuario ADD CONSTRAINT ck_credencial_expiracao CHECK (credencial_temporaria_expira_em IS NULL OR credencial_pendente = TRUE);
