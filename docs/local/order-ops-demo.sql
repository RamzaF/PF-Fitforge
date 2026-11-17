USE fitforge_demo;
SET NAMES utf8mb4;
-- Public demo-only credentials inherited from the baseline fictional fixture.
INSERT INTO usuario(id_usuario,username,password,nombre,correo,activo)
 SELECT 101,'ops_admin',password,'Admin OrderOps Demo','admin-orderops@fitforge.example',true FROM usuario WHERE id_usuario=1;
INSERT INTO usuario(id_usuario,username,password,nombre,correo,activo)
 SELECT 102,'ops_cliente',password,'Cliente OrderOps Demo','cliente-orderops@fitforge.example',true FROM usuario WHERE id_usuario=2;
INSERT INTO usuario(id_usuario,username,password,nombre,correo,activo)
 SELECT 103,'ops_otro',password,'Otro OrderOps Demo','otro-orderops@fitforge.example',true FROM usuario WHERE id_usuario=2;
INSERT INTO rol(nombre,id_usuario) VALUES ('ROLE_ADMIN',101),('ROLE_USER',102),('ROLE_USER',103);
