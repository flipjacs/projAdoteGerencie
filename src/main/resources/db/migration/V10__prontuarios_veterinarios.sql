-- Composite key prevents a record from assigning an animal to a different ONG.
ALTER TABLE animal ADD CONSTRAINT uq_animal_id_ong UNIQUE (id,ong_id);
CREATE TABLE prontuario (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 animal_id BIGINT NOT NULL,
 ong_id INT NOT NULL,
 registrado_por INT NOT NULL,
 tipo VARCHAR(20) NOT NULL CHECK (tipo IN ('CONSULTA','VACINA','VERMIFUGACAO','EXAME','CIRURGIA','TRATAMENTO','MEDICACAO','OBSERVACAO')),
 titulo VARCHAR(120) NOT NULL CHECK (CHAR_LENGTH(TRIM(titulo)) > 0),
 descricao TEXT NOT NULL,
 data_procedimento DATE NOT NULL,
 profissional_responsavel VARCHAR(120) NULL,
 medicamentos TEXT NULL,
 observacoes TEXT NULL,
 criado_em TIMESTAMP(6) NOT NULL,
 atualizado_em TIMESTAMP(6) NOT NULL,
 versao BIGINT NOT NULL DEFAULT 0 CHECK (versao >= 0),
 FOREIGN KEY (animal_id,ong_id) REFERENCES animal(id,ong_id),
 FOREIGN KEY (ong_id) REFERENCES ong(id),
 FOREIGN KEY (registrado_por) REFERENCES usuario(id)
);
CREATE INDEX idx_prontuario_animal_data ON prontuario(animal_id,ong_id,data_procedimento,id);
CREATE INDEX idx_prontuario_tipo_data ON prontuario(animal_id,tipo,data_procedimento);
CREATE TABLE correcao_prontuario (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 prontuario_id BIGINT NOT NULL,
 corrigido_por INT NOT NULL,
 justificativa VARCHAR(2000) NOT NULL CHECK (CHAR_LENGTH(TRIM(justificativa)) > 0),
 dados_anteriores MEDIUMTEXT NOT NULL,
 dados_novos MEDIUMTEXT NOT NULL,
 versao_anterior BIGINT NOT NULL CHECK (versao_anterior >= 0),
 versao_posterior BIGINT NOT NULL,
 criado_em TIMESTAMP(6) NOT NULL,
 FOREIGN KEY (prontuario_id) REFERENCES prontuario(id),
 FOREIGN KEY (corrigido_por) REFERENCES usuario(id),
 UNIQUE (prontuario_id,versao_posterior),
 CHECK (versao_posterior=versao_anterior+1)
);
CREATE INDEX idx_correcao_prontuario_data ON correcao_prontuario(prontuario_id,criado_em,id);
