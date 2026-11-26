CREATE TABLE solicitacao_adocao (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 animal_id BIGINT NOT NULL,
 usuario_id INT NOT NULL,
 ong_id INT NOT NULL,
 status VARCHAR(16) NOT NULL,
 mensagem VARCHAR(2000) NULL,
 justificativa_rejeicao VARCHAR(1000) NULL,
 justificativa_cancelamento VARCHAR(1000) NULL,
 analisado_por INT NULL,
 analisado_em TIMESTAMP NULL,
 cancelado_por INT NULL,
 cancelado_em TIMESTAMP NULL,
 criado_em TIMESTAMP NOT NULL,
 atualizado_em TIMESTAMP NOT NULL,
 versao BIGINT NOT NULL DEFAULT 0,
 FOREIGN KEY (animal_id) REFERENCES animal(id),
 FOREIGN KEY (usuario_id) REFERENCES usuario(id),
 FOREIGN KEY (ong_id) REFERENCES ong(id),
 FOREIGN KEY (analisado_por) REFERENCES usuario(id),
 FOREIGN KEY (cancelado_por) REFERENCES usuario(id),
 CHECK (status IN ('PENDENTE','APROVADA','REJEITADA','CANCELADA','CONCLUIDA'))
);
CREATE INDEX idx_solicitacao_usuario ON solicitacao_adocao(usuario_id,id);
CREATE INDEX idx_solicitacao_ong ON solicitacao_adocao(ong_id,status,id);
CREATE INDEX idx_solicitacao_animal ON solicitacao_adocao(animal_id,status,usuario_id);
CREATE TABLE adocao (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 solicitacao_id BIGINT NOT NULL UNIQUE,
 animal_id BIGINT NOT NULL UNIQUE,
 usuario_id INT NOT NULL,
 ong_id INT NOT NULL,
 confirmado_por INT NOT NULL,
 data_entrega TIMESTAMP NOT NULL,
 FOREIGN KEY (solicitacao_id) REFERENCES solicitacao_adocao(id),
 FOREIGN KEY (animal_id) REFERENCES animal(id),
 FOREIGN KEY (usuario_id) REFERENCES usuario(id),
 FOREIGN KEY (ong_id) REFERENCES ong(id),
 FOREIGN KEY (confirmado_por) REFERENCES usuario(id)
);
CREATE TABLE historico_adocao (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 solicitacao_id BIGINT NOT NULL,
 ator_id INT NOT NULL,
 operacao VARCHAR(40) NOT NULL,
 anterior VARCHAR(16) NULL,
 posterior VARCHAR(16) NOT NULL,
 justificativa VARCHAR(1000) NULL,
 ocorrido_em TIMESTAMP NOT NULL,
 FOREIGN KEY (solicitacao_id) REFERENCES solicitacao_adocao(id),
 FOREIGN KEY (ator_id) REFERENCES usuario(id)
);
CREATE INDEX idx_historico_solicitacao ON historico_adocao(solicitacao_id,id);
