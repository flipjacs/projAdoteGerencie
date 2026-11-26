CREATE TABLE demonstrativo_transparencia (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 ong_id INT NOT NULL,
 periodo_inicio DATE NOT NULL,
 periodo_fim DATE NOT NULL,
 titulo VARCHAR(120) NOT NULL,
 descricao VARCHAR(2000) NULL,
 status VARCHAR(12) NOT NULL DEFAULT 'RASCUNHO' CHECK (status IN ('RASCUNHO','PUBLICADO')),
 dados_consolidados TEXT NOT NULL,
 publicado_por INT NULL,
 publicado_em TIMESTAMP(6) NULL,
 despublicado_em TIMESTAMP(6) NULL,
 criado_em TIMESTAMP(6) NOT NULL,
 atualizado_em TIMESTAMP(6) NOT NULL,
 versao BIGINT NOT NULL DEFAULT 0,
 FOREIGN KEY (ong_id) REFERENCES ong(id),
 FOREIGN KEY (publicado_por) REFERENCES usuario(id),
 CHECK (periodo_fim >= periodo_inicio),
 CHECK (status <> 'PUBLICADO' OR (publicado_por IS NOT NULL AND publicado_em IS NOT NULL))
);
CREATE INDEX idx_demonstrativo_publico ON demonstrativo_transparencia(ong_id,status,publicado_em,id);
CREATE INDEX idx_demonstrativo_periodo ON demonstrativo_transparencia(ong_id,periodo_inicio,periodo_fim);
