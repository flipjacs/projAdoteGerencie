CREATE UNIQUE INDEX uk_ong_identificacao ON ong(cnpj_cpf);
ALTER TABLE usuario ADD COLUMN credencial_pendente BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE ong ADD COLUMN situacao VARCHAR(16) NOT NULL DEFAULT 'ATIVA';
ALTER TABLE ong ADD CONSTRAINT ck_ong_situacao CHECK (situacao IN ('ATIVA','SUSPENSA'));
ALTER TABLE ong ADD COLUMN criado_em TIMESTAMP NULL;
ALTER TABLE ong ADD COLUMN atualizado_em TIMESTAMP NULL;
ALTER TABLE ong ADD COLUMN criado_por INT NULL;
ALTER TABLE ong ADD COLUMN versao BIGINT NOT NULL DEFAULT 0;
ALTER TABLE ong ADD CONSTRAINT fk_ong_criador FOREIGN KEY (criado_por) REFERENCES usuario(id);
CREATE TABLE auditoria_institucional (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 ong_id INT NULL,
 ator_id INT NOT NULL,
 usuario_alvo_id INT NULL,
 operacao VARCHAR(40) NOT NULL,
 estado_anterior VARCHAR(32) NULL,
 estado_posterior VARCHAR(32) NULL,
 motivo VARCHAR(500) NULL,
 criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_auditoria_ong FOREIGN KEY (ong_id) REFERENCES ong(id),
 CONSTRAINT fk_auditoria_ator FOREIGN KEY (ator_id) REFERENCES usuario(id),
 CONSTRAINT fk_auditoria_usuario_alvo FOREIGN KEY (usuario_alvo_id) REFERENCES usuario(id)
);
CREATE TABLE bootstrap_global_guard (id INT NOT NULL PRIMARY KEY, concluido BOOLEAN NOT NULL DEFAULT FALSE);
INSERT INTO bootstrap_global_guard(id) VALUES (1);
