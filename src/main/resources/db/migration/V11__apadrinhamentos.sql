CREATE TABLE apadrinhamento (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 animal_id BIGINT NOT NULL, ong_id INT NOT NULL, usuario_id INT NOT NULL,
 modalidade VARCHAR(10) NOT NULL CHECK (modalidade IN ('UNICO','MENSAL')),
 valor DECIMAL(12,2) NOT NULL CHECK (valor>0),
 status VARCHAR(12) NOT NULL CHECK (status IN ('ATIVO','ENCERRADO')),
 data_inicio DATE NOT NULL, data_encerramento DATE NULL,
 motivo_encerramento VARCHAR(40) NULL CHECK (motivo_encerramento IN ('CANCELAMENTO_USUARIO','ENCERRAMENTO_ONG','ADOCAO_CONCLUIDA','FALECIMENTO_ANIMAL')),
 justificativa_encerramento VARCHAR(1000) NULL,
 criado_em TIMESTAMP(6) NOT NULL, atualizado_em TIMESTAMP(6) NOT NULL,
 versao BIGINT NOT NULL DEFAULT 0,
 UNIQUE(ong_id,id),
 FOREIGN KEY(animal_id,ong_id) REFERENCES animal(id,ong_id),
 FOREIGN KEY(usuario_id) REFERENCES usuario(id), FOREIGN KEY(ong_id) REFERENCES ong(id),
 CHECK ((status='ATIVO' AND data_encerramento IS NULL AND motivo_encerramento IS NULL)
  OR (status='ENCERRADO' AND data_encerramento IS NOT NULL AND motivo_encerramento IS NOT NULL AND data_encerramento>=data_inicio))
);
CREATE INDEX idx_apad_usuario ON apadrinhamento(usuario_id,status,criado_em,id);
CREATE INDEX idx_apad_ong ON apadrinhamento(ong_id,status,criado_em,id);
CREATE INDEX idx_apad_animal ON apadrinhamento(animal_id,status,id);
CREATE TABLE parcela_apadrinhamento (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 apadrinhamento_id BIGINT NOT NULL, ong_id INT NOT NULL,
 numero INT NOT NULL CHECK (numero BETWEEN 1 AND 240),
 valor DECIMAL(12,2) NOT NULL CHECK(valor>0),
 vencimento DATE NOT NULL,
 status VARCHAR(12) NOT NULL CHECK(status IN ('PENDENTE','CONFIRMADA','CANCELADA')),
 confirmado_em TIMESTAMP(6) NULL, confirmado_por INT NULL,
 criado_em TIMESTAMP(6) NOT NULL, atualizado_em TIMESTAMP(6) NOT NULL,
 versao BIGINT NOT NULL DEFAULT 0,
 UNIQUE(apadrinhamento_id,numero), UNIQUE(apadrinhamento_id,vencimento), UNIQUE(ong_id,id),
 FOREIGN KEY(ong_id,apadrinhamento_id) REFERENCES apadrinhamento(ong_id,id),
 FOREIGN KEY(confirmado_por) REFERENCES usuario(id),
 CHECK ((status='CONFIRMADA' AND confirmado_em IS NOT NULL AND confirmado_por IS NOT NULL)
  OR (status IN ('PENDENTE','CANCELADA') AND confirmado_em IS NULL AND confirmado_por IS NULL))
);
CREATE INDEX idx_parcela_status_vencimento ON parcela_apadrinhamento(apadrinhamento_id,status,vencimento,numero);
