CREATE TABLE doacao (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 ong_id INT NOT NULL,
 usuario_id INT NOT NULL,
 valor DECIMAL(12,2) NOT NULL CHECK (valor > 0),
 status VARCHAR(16) NOT NULL DEFAULT 'PENDENTE' CHECK (status IN ('PENDENTE','CONFIRMADA','CANCELADA')),
 mensagem VARCHAR(2000) NULL,
 confirmado_por INT NULL,
 confirmado_em TIMESTAMP(6) NULL,
 cancelado_por INT NULL,
 cancelado_em TIMESTAMP(6) NULL,
 justificativa_cancelamento VARCHAR(1000) NULL,
 criado_em TIMESTAMP(6) NOT NULL,
 atualizado_em TIMESTAMP(6) NOT NULL,
 versao BIGINT NOT NULL DEFAULT 0,
 UNIQUE (ong_id,id),
 FOREIGN KEY (ong_id) REFERENCES ong(id),
 FOREIGN KEY (usuario_id) REFERENCES usuario(id),
 FOREIGN KEY (confirmado_por) REFERENCES usuario(id),
 FOREIGN KEY (cancelado_por) REFERENCES usuario(id),
 CHECK ((status='PENDENTE' AND confirmado_em IS NULL AND cancelado_em IS NULL)
     OR (status='CONFIRMADA' AND confirmado_em IS NOT NULL AND confirmado_por IS NOT NULL AND cancelado_em IS NULL)
     OR (status='CANCELADA' AND cancelado_em IS NOT NULL AND cancelado_por IS NOT NULL AND confirmado_em IS NULL))
);
CREATE INDEX idx_doacao_ong ON doacao(ong_id,status,criado_em,id);
CREATE INDEX idx_doacao_usuario ON doacao(usuario_id,criado_em,id);

CREATE TABLE movimentacao_financeira (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 ong_id INT NOT NULL,
 tipo VARCHAR(16) NOT NULL CHECK (tipo IN ('RECEITA','DESPESA')),
 categoria VARCHAR(32) NOT NULL,
 descricao VARCHAR(255) NOT NULL,
 valor DECIMAL(12,2) NOT NULL CHECK (valor > 0),
 status VARCHAR(16) NOT NULL CHECK (status IN ('PENDENTE','APROVADA','REALIZADA','CANCELADA')),
 doacao_id BIGINT NULL UNIQUE,
 registrado_por INT NOT NULL,
 aprovado_por INT NULL,
 aprovado_em TIMESTAMP(6) NULL,
 confirmado_por INT NULL,
 confirmado_em TIMESTAMP(6) NULL,
 cancelado_por INT NULL,
 cancelado_em TIMESTAMP(6) NULL,
 justificativa_cancelamento VARCHAR(1000) NULL,
 criado_em TIMESTAMP(6) NOT NULL,
 atualizado_em TIMESTAMP(6) NOT NULL,
 versao BIGINT NOT NULL DEFAULT 0,
 FOREIGN KEY (ong_id) REFERENCES ong(id),
 FOREIGN KEY (ong_id,doacao_id) REFERENCES doacao(ong_id,id),
 FOREIGN KEY (registrado_por) REFERENCES usuario(id),
 FOREIGN KEY (aprovado_por) REFERENCES usuario(id),
 FOREIGN KEY (confirmado_por) REFERENCES usuario(id),
 FOREIGN KEY (cancelado_por) REFERENCES usuario(id),
 CHECK ((tipo='RECEITA' AND categoria IN ('DOACAO','EVENTO','OUTRA_RECEITA') AND status<>'APROVADA')
     OR (tipo='DESPESA' AND categoria IN ('ALIMENTACAO','MEDICAMENTOS','VETERINARIO','TRANSPORTE','MANUTENCAO','OUTRA_DESPESA'))),
 CHECK ((doacao_id IS NULL AND categoria<>'DOACAO') OR (doacao_id IS NOT NULL AND tipo='RECEITA' AND categoria='DOACAO' AND status='REALIZADA')),
 CHECK ((status='PENDENTE' AND aprovado_em IS NULL AND confirmado_em IS NULL AND cancelado_em IS NULL)
     OR (status='APROVADA' AND tipo='DESPESA' AND aprovado_em IS NOT NULL AND aprovado_por IS NOT NULL AND confirmado_em IS NULL AND cancelado_em IS NULL)
     OR (status='REALIZADA' AND confirmado_em IS NOT NULL AND confirmado_por IS NOT NULL AND cancelado_em IS NULL AND (tipo='RECEITA' OR (aprovado_em IS NOT NULL AND aprovado_por IS NOT NULL)))
     OR (status='CANCELADA' AND cancelado_em IS NOT NULL AND cancelado_por IS NOT NULL AND confirmado_em IS NULL))
);
CREATE INDEX idx_movimento_ong ON movimentacao_financeira(ong_id,tipo,status,criado_em,id);
CREATE INDEX idx_movimento_confirmacao ON movimentacao_financeira(ong_id,status,confirmado_em);

CREATE TABLE historico_financeiro (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 doacao_id BIGINT NULL,
 movimentacao_id BIGINT NULL,
 ator_id INT NOT NULL,
 operacao VARCHAR(40) NOT NULL,
 anterior VARCHAR(16) NULL,
 posterior VARCHAR(16) NOT NULL,
 justificativa VARCHAR(1000) NULL,
 ocorrido_em TIMESTAMP(6) NOT NULL,
 CHECK ((doacao_id IS NOT NULL AND movimentacao_id IS NULL) OR (doacao_id IS NULL AND movimentacao_id IS NOT NULL)),
 FOREIGN KEY (doacao_id) REFERENCES doacao(id),
 FOREIGN KEY (movimentacao_id) REFERENCES movimentacao_financeira(id),
 FOREIGN KEY (ator_id) REFERENCES usuario(id)
);
CREATE INDEX idx_historico_doacao ON historico_financeiro(doacao_id,id);
CREATE INDEX idx_historico_movimento ON historico_financeiro(movimentacao_id,id);
