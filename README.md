# Uruguay Mnatural

Plataforma que reúne las experiencias turísticas de todo Uruguay: los emprendimientos publican sus actividades y los turistas las encuentran, las reservan y las pagan en un solo lugar. Proyecto de **Lúmina Labs** para Proyecto TIC 2026 (Universidad de Montevideo).

Hay tres tipos de usuario:

- **Turistas:** buscan actividades, reservan, pagan con tarjeta, cancelan y valoran.
- **Prestadores:** registran su emprendimiento, publican actividades y horarios, y gestionan las reservas que reciben.
- **Administradores:** aprueban, suspenden y revisan emprendimientos, y mantienen localidades, categorías, valoraciones y usuarios.

## Tecnologías

| Parte | Qué usamos |
| --- | --- |
| Backend | Java 25, Spring Boot 4.1, Maven |
| Frontend | Thymeleaf, HTML, CSS y JavaScript sin frameworks |
| Base de datos | PostgreSQL 17 en Docker; el esquema lo crea y lo actualiza Flyway |
| Seguridad | Spring Security; las contraseñas se guardan con BCrypt |
| Pagos | Pasarela simulada propia, o el sandbox de Mercado Pago |
| Clima | API de Open-Meteo, que es pública y no necesita clave |
| Correo | Mailpit en desarrollo: recibe los correos y los muestra en el navegador |
| API pública | REST, documentada con OpenAPI (springdoc) |

## Cómo levantar el proyecto

Se necesitan **Java 25** y **Docker Desktop** abierto. Maven no hace falta instalarlo: el proyecto trae `mvnw`.

1. Levantar la base de datos y el servidor de correo:

   ```bash
   docker compose up -d
   ```

   Esto crea dos contenedores: `luminalabs-db` (PostgreSQL, en el puerto **5433**) y `luminalabs-mail` (Mailpit).

2. Arrancar la aplicación:

   ```bash
   ./mvnw spring-boot:run
   ```

   En Windows, desde `cmd` o PowerShell: `mvnw.cmd spring-boot:run`. También se puede correr `LuminalabsApplication` desde IntelliJ.

3. Entrar a **http://localhost:8080**.

La primera vez, la aplicación crea las tablas y carga los datos de demostración (ver abajo). Las veces siguientes los deja como están.

### Direcciones útiles

| Dirección | Qué es |
| --- | --- |
| http://localhost:8080 | El sitio |
| http://localhost:8025 | Mailpit: todos los correos que manda la aplicación |
| http://localhost:8080/api/docs | Documentación de la API pública, para leerla y probarla |
| http://localhost:8080/api/openapi.json | Especificación OpenAPI de la API |

## Usuarios de demostración

| Usuario | Contraseña | Rol |
| --- | --- | --- |
| `demo.turista` | `Demo12345` | Turista, con reservas próximas, una para valorar y una ya valorada |
| `demo.prestador` | `Demo12345` | Prestador de Costa Este Experiencias y Sierras de Lavalleja |
| `admin` | `Admin12345` | Administrador |

Todos los demás usuarios de demo tienen la contraseña `Demo12345`; sus correos terminan en `@uruguaymnatural.uy` y llegan a Mailpit. Los datos están en `src/main/resources/demo/data.json`: 12 emprendimientos, 32 actividades en los 19 departamentos, reseñas, reservas y fotos. Los créditos de las fotos están en `src/main/resources/demo/photos/CREDITOS.md`.

Para no cargarlos, poner `app.demo-data.enabled=false` en `application.properties`. Para volver a cargarlos desde cero, borrar la base:

```bash
docker compose down -v
docker compose up -d
```

## Pagos

### Pasarela simulada (por defecto)

Corre dentro de la misma aplicación y responde según el número de tarjeta. El titular, el vencimiento (futuro) y el código pueden ser cualquiera.

| Tarjeta | Resultado |
| --- | --- |
| `4111 1111 1111 1111` (o cualquier otra) | Aprobado |
| `4000 0000 0000 0002` | Rechazado |
| `4000 0000 0000 0119` | Error de la pasarela |
| `4000 0000 0000 3220` | No responde a tiempo, y después figura como aprobado |
| `4000 0000 0000 3246` | No responde a tiempo, y después figura como rechazado |
| `4000 0000 0000 0069` | Aprobado, pero las devoluciones fallan las primeras veces (para ver los reintentos) |

### Sandbox de Mercado Pago

1. Crear el archivo `mercadopago-credentials.properties` en la carpeta del proyecto con las credenciales **de prueba**:

   ```properties
   app.mercadopago.access-token=...
   app.mercadopago.public-key=...
   ```

   Ese archivo está en el `.gitignore`: **nunca se sube a Git**.

2. Arrancar con el perfil `mercadopago`:

   ```bash
   SPRING_PROFILES_ACTIVE=mercadopago ./mvnw spring-boot:run
   ```

   En PowerShell: `$env:SPRING_PROFILES_ACTIVE="mercadopago"; .\mvnw.cmd spring-boot:run`.

La pantalla de pago muestra las tarjetas de prueba del sandbox. Más detalles en `src/main/resources/application-mercadopago.properties`.

## Tests

```bash
./mvnw test
```

Usan una base H2 en memoria, así que no necesitan Docker ni internet. Hay tests unitarios de los servicios de cada caso de uso, de las entidades y de la API pública, más algunos de persistencia.

## Estructura del código

Todo está en `src/main/java/uy/edu/um/luminalabs`, organizado en capas (MVC):

| Paquete | Qué tiene |
| --- | --- |
| `controllers` | Las páginas del sitio: reciben el pedido, llaman al servicio y eligen la plantilla |
| `api` | La API REST pública (`/api/v1/...`) |
| `services` | Las reglas de negocio de cada caso de uso |
| `repositories` | El acceso a la base (Spring Data JPA) |
| `entities` | El modelo: usuarios, emprendimientos, actividades, horarios, reservas, pagos, etc. |
| `dto` | Los formularios y sus validaciones |
| `payments` y `mockgateway` | La integración con la pasarela de pagos y la pasarela simulada |
| `weather` | El pronóstico del clima (Open-Meteo) |
| `notifications` y `events` | Los correos: cada cambio publica un evento y el correo sale después de confirmarse la transacción |
| `security` | Inicio de sesión, roles y cierre de sesiones |
| `config` | Configuración general y la carga de datos iniciales y de demo |

Otros lugares importantes:

- `src/main/resources/templates`: las pantallas (Thymeleaf).
- `src/main/resources/static`: CSS, JavaScript e imágenes.
- `src/main/resources/db/migration`: las migraciones de Flyway (`V1__...`, `V2__...`). Para cambiar el esquema se agrega una migración nueva; **nunca se modifica una que ya está en Git**.
- `src/main/resources/application.properties`: la configuración, comentada.

## API pública

Sin autenticación, de solo lectura, en `/api/v1`:

| Endpoint | Qué devuelve |
| --- | --- |
| `GET /businesses` | Los emprendimientos aprobados |
| `GET /businesses/verification` | Si un RUT o cédula corresponde a un emprendimiento aprobado |
| `GET /activities/day` | Las actividades de un día, con filtro por lugar |
| `GET /activities/top` | Las 3 mejor valoradas del mes, por departamento |
| `GET /activities/search` | Búsqueda por palabra clave |

Los parámetros y ejemplos de cada uno están en http://localhost:8080/api/docs.
