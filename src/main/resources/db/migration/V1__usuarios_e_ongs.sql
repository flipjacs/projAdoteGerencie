CREATE TABLE ong (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(120) NOT NULL,
    cnpj_cpf VARCHAR(20),
    email VARCHAR(120) NOT NULL,
    telefone VARCHAR(20),
    cidade VARCHAR(80),
    estado CHAR(2)
);

CREATE TABLE usuario (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ong_id INT NOT NULL,
    nome VARCHAR(100) NOT NULL,
    email VARCHAR(120) NOT NULL UNIQUE,
    senha_hash VARCHAR(255) NOT NULL,
    perfil VARCHAR(20) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_usuario_ong FOREIGN KEY (ong_id) REFERENCES ong(id)
);
