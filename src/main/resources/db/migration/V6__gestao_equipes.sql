ALTER TABLE usuario ADD COLUMN versao BIGINT NOT NULL DEFAULT 0;
ALTER TABLE usuario ADD COLUMN token_versao BIGINT NOT NULL DEFAULT 0;
CREATE INDEX idx_usuario_equipe ON usuario(ong_id,perfil,ativo,credencial_pendente);
CREATE TABLE auditoria_equipe (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 ong_id INT NOT NULL,
 funcionario_id INT NOT NULL,
 ator_id INT NOT NULL,
 operacao VARCHAR(40) NOT NULL,
 anterior VARCHAR(100) NULL,
 posterior VARCHAR(100) NULL,
 motivo VARCHAR(500) NULL,
 criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_equipe_audit_ong FOREIGN KEY (ong_id) REFERENCES ong(id),
 CONSTRAINT fk_equipe_audit_funcionario FOREIGN KEY (funcionario_id) REFERENCES usuario(id),
 CONSTRAINT fk_equipe_audit_ator FOREIGN KEY (ator_id) REFERENCES usuario(id)
);
CREATE INDEX idx_auditoria_equipe_ong ON auditoria_equipe(ong_id,id);
