CREATE TABLE animal (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 ong_id INT NOT NULL,
 nome VARCHAR(100) NOT NULL,
 especie VARCHAR(20) NOT NULL,
 sexo VARCHAR(20) NOT NULL,
 porte VARCHAR(20) NOT NULL,
 nascimento_estimado DATE NULL,
 descricao VARCHAR(2000) NULL,
 historia VARCHAR(2000) NULL,
 data_resgate DATE NULL,
 origem_resgate VARCHAR(255) NULL,
 observacoes_resgate VARCHAR(2000) NULL,
 data_entrada DATE NOT NULL,
 situacao VARCHAR(30) NOT NULL,
 situacao_atualizada_em TIMESTAMP NOT NULL,
 situacao_alterada_por INT NOT NULL,
 ativo BOOLEAN NOT NULL DEFAULT TRUE,
 publicado BOOLEAN NOT NULL DEFAULT FALSE,
 criado_em TIMESTAMP NOT NULL,
 atualizado_em TIMESTAMP NOT NULL,
 versao BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT fk_animal_ong FOREIGN KEY (ong_id) REFERENCES ong(id),
 CONSTRAINT fk_animal_responsavel FOREIGN KEY (situacao_alterada_por) REFERENCES usuario(id)
);
CREATE INDEX idx_animal_ong_situacao ON animal (ong_id, situacao, ativo);
CREATE INDEX idx_animal_publico ON animal (publicado, ativo, situacao);
CREATE TABLE animal_foto (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 animal_id BIGINT NOT NULL,
 arquivo VARCHAR(80) NOT NULL UNIQUE,
 tipo VARCHAR(20) NOT NULL,
 principal BOOLEAN NOT NULL DEFAULT FALSE,
 criado_em TIMESTAMP NOT NULL,
 CONSTRAINT fk_foto_animal FOREIGN KEY (animal_id) REFERENCES animal(id)
);
CREATE INDEX idx_foto_animal ON animal_foto (animal_id);
