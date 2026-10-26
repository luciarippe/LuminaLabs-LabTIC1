-- V12: foto propia de cada departamento (la carga el administrador). Se muestra en "Destinos" de la portada;
-- si un departamento no tiene, se usa la de alguna de sus localidades. Los departamentos son una lista fija
-- (enum Department), asi que la clave es su nombre.

CREATE TABLE department_photos (
    department varchar(30) NOT NULL,
    photo_id bigint NOT NULL UNIQUE,
    PRIMARY KEY (department),
    CONSTRAINT fk_department_photos_photo FOREIGN KEY (photo_id) REFERENCES images
);
