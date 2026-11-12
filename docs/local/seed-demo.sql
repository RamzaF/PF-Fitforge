USE fitforge_demo;
INSERT INTO categoria (id_categoria,descripcion,activo) VALUES (1,'Ropa demo',true),(2,'Accesorios demo',true);
INSERT INTO producto (id_producto,id_categoria,descripcion,detalle,precio,imagen,activo) VALUES
(1,1,'Camiseta Demo','Producto ficticio para verificacion local',25.0,'/uploads/gymshark_camisa.png',true),
(2,2,'Guantes Demo','Accesorio ficticio sin venta real',15.0,'/uploads/guantes_gym.png',true);
INSERT INTO color (id_color,nombre_color) VALUES (1,'Negro'),(2,'Azul');
INSERT INTO talla (id_talla,nombre_talla) VALUES (1,'M'),(2,'L');
INSERT INTO producto_color_talla (id_producto,id_color,id_talla,existencias) VALUES (1,1,1,5),(1,2,2,0),(2,1,1,3);
-- New demo-only password hash generated with BCrypt inside the pinned Java container.
INSERT INTO usuario (id_usuario,username,password,nombre,correo,activo) VALUES
(1,'demo_admin','$2a$10$3EuEhsqJgW5ZJx0Xq1HhQO8zUTOEJA2MQHtJb2KFY1M1umF2xlSc6','Admin ficticio','admin@fitforge.example',true),
(2,'demo_cliente','$2a$10$3EuEhsqJgW5ZJx0Xq1HhQO8zUTOEJA2MQHtJb2KFY1M1umF2xlSc6','Cliente ficticio','cliente@fitforge.example',true);
INSERT INTO rol (nombre,id_usuario) VALUES ('ROLE_ADMIN',1),('ROLE_USER',2);
