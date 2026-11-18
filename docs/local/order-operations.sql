-- Only initialize a NEW database after 01-schema.sql and 02-seed.sql.
USE fitforge_demo;
SET NAMES utf8mb4;
ALTER TABLE factura
 ADD destinatario VARCHAR(80) NOT NULL,
 ADD telefono_entrega VARCHAR(20) NOT NULL,
 ADD direccion_entrega VARCHAR(240) NOT NULL,
 ADD nota_entrega VARCHAR(240) NOT NULL,
 ADD consentimiento_demo BOOLEAN NOT NULL,
 ADD CONSTRAINT ck_factura_estado CHECK (estado IN ('Pendiente','En preparación','Despachado'));
CREATE TABLE pedido_evento (
 id_evento BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 id_factura BIGINT NOT NULL,
 id_usuario INT NOT NULL,
 fecha TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 origen VARCHAR(30) NOT NULL,
 destino VARCHAR(30) NOT NULL,
 UNIQUE KEY uq_pedido_arista(id_factura,origen,destino),
 FOREIGN KEY (id_factura) REFERENCES factura(id_factura),
 FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario),
 CONSTRAINT ck_pedido_arista CHECK ((origen='Pendiente' AND destino='En preparación') OR (origen='En preparación' AND destino='Despachado'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX ix_factura_estado_id ON factura(estado,id_factura);
CREATE INDEX ix_factura_owner_id ON factura(id_usuario,id_factura);
