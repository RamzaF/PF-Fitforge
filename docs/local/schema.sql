-- Reviewed demo-only schema; no historical accounts, destinations or data.
USE fitforge_demo;
CREATE TABLE categoria (
  id_categoria BIGINT NOT NULL AUTO_INCREMENT,
  descripcion VARCHAR(50) NOT NULL,
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  PRIMARY KEY (id_categoria),
  UNIQUE KEY uq_categoria_descripcion (descripcion)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE producto (
  id_producto BIGINT NOT NULL AUTO_INCREMENT,
  id_categoria BIGINT NOT NULL,
  descripcion VARCHAR(100) NOT NULL,
  detalle TEXT NOT NULL,
  precio DOUBLE NOT NULL,
  imagen VARCHAR(255),
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  PRIMARY KEY (id_producto),
  FOREIGN KEY (id_categoria) REFERENCES categoria(id_categoria)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE color (
  id_color BIGINT NOT NULL AUTO_INCREMENT,
  nombre_color VARCHAR(50) NOT NULL,
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  PRIMARY KEY (id_color),
  UNIQUE KEY uq_color_nombre (nombre_color)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE talla (
  id_talla BIGINT NOT NULL AUTO_INCREMENT,
  nombre_talla VARCHAR(50) NOT NULL,
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  PRIMARY KEY (id_talla),
  UNIQUE KEY uq_talla_nombre (nombre_talla)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE producto_color_talla (
  id_producto_color_talla BIGINT NOT NULL AUTO_INCREMENT,
  id_producto BIGINT NOT NULL,
  id_color BIGINT NULL,
  id_talla BIGINT NULL,
  existencias INT NOT NULL CHECK (existencias >= 0),
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  PRIMARY KEY (id_producto_color_talla),
  UNIQUE KEY uq_pct_variante (id_producto, id_color, id_talla),
  FOREIGN KEY (id_producto) REFERENCES producto(id_producto),
  FOREIGN KEY (id_color) REFERENCES color(id_color),
  FOREIGN KEY (id_talla) REFERENCES talla(id_talla)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE usuario (
  id_usuario INT NOT NULL AUTO_INCREMENT,
  username VARCHAR(20) NOT NULL,
  password VARCHAR(512) NOT NULL,
  nombre VARCHAR(50) NOT NULL,
  correo VARCHAR(100) NOT NULL,
  telefono VARCHAR(15),
  avatar VARCHAR(255),
  activo BOOLEAN DEFAULT TRUE,
  PRIMARY KEY (id_usuario)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE factura (
  id_factura BIGINT NOT NULL AUTO_INCREMENT,
  id_usuario INT NOT NULL,
  clave_confirmacion CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  huella_carrito CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  UNIQUE KEY uq_factura_confirmacion (id_usuario, clave_confirmacion),
  fecha DATE NOT NULL,
  total DOUBLE NOT NULL CHECK (total >= 0),
  estado VARCHAR(255) DEFAULT 'Pendiente',
  PRIMARY KEY (id_factura),
  FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE venta (
  id_venta BIGINT NOT NULL AUTO_INCREMENT,
  id_factura BIGINT NOT NULL,
  id_producto_color_talla BIGINT NOT NULL,
  precio DOUBLE NOT NULL CHECK (precio >= 0),
  cantidad INT NOT NULL CHECK (cantidad > 0),
  PRIMARY KEY (id_venta),
  FOREIGN KEY (id_factura) REFERENCES factura(id_factura),
  FOREIGN KEY (id_producto_color_talla) REFERENCES producto_color_talla(id_producto_color_talla)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE rol (
  id_rol BIGINT NOT NULL AUTO_INCREMENT,
  nombre VARCHAR(20) NOT NULL,
  id_usuario INT NOT NULL,
  PRIMARY KEY (id_rol),
  FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE request_matcher (
  id_request_matcher BIGINT AUTO_INCREMENT NOT NULL,
  pattern VARCHAR(255) NOT NULL,
  role_name VARCHAR(50) NOT NULL,
  PRIMARY KEY (id_request_matcher)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE contacto (
  id_contacto BIGINT NOT NULL AUTO_INCREMENT,
  nombre VARCHAR(50) NOT NULL,
  apellido VARCHAR(50) NOT NULL,
  correo VARCHAR(100) NOT NULL,
  mensaje VARCHAR(255),
  activo BOOLEAN DEFAULT TRUE,
  PRIMARY KEY (id_contacto)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE USER 'fitforge_app'@'%' IDENTIFIED BY 'demo-only-fitforge-app';
GRANT SELECT, INSERT, UPDATE, DELETE ON fitforge_demo.* TO 'fitforge_app'@'%';
