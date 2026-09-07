# MisFinanzas — Backend

Aplicación de finanzas personales y compartidas (pareja / piso compartido), con dashboard, categorización de movimientos, import/export de Excel y, en fases futuras, informes generados por IA y conexión bancaria (Open Banking).

Este repositorio contiene el **backend** (API REST). El frontend (React / React Native) vive en un repositorio aparte.

## Stack

- **Java 21/22** + **Spring Boot 4.1.1**
- **PostgreSQL 16**
- **Spring Security** con JWT propio (login vía Google OAuth2)
- **JPA / Hibernate**
- **JUnit 5 + Mockito + AssertJ** para tests
- Configuración en **YAML** (`application.yml`)
- Arquitectura: **monolito modular** con **arquitectura hexagonal** por módulo

Para el razonamiento detrás de estas decisiones, ver [`docs/ARQUITECTURA_Y_TESTS.docx`](docs/ARQUITECTURA_Y_TESTS.docx).

## Estructura de paquetes

Paquete raíz: `com.acruzdb.misfinanzas`

```
com.acruzdb.misfinanzas/
├── auth/            → Usuarios, login (Google + JWT), sesiones
├── categories/       → Categorías/etiquetas de movimientos
├── transactions/      → Ingresos y gastos, resumen mensual
├── shared/           → Grupos compartidos (households)
└── importexport/       → Import/export de Excel (pendiente de construir)
```

Cada módulo sigue la misma estructura interna:

```
<módulo>/
├── domain/          → Entidades JPA, reglas de negocio propias de la entidad
├── application/       → Services: casos de uso y lógica de negocio
├── infrastructure/     → Controllers REST, Repositories, config específica
└── dto/            → Records de entrada/salida de la API
```

## Puesta en marcha en local

### 1. Requisitos

- Java 21+ (probado con Java 22)
- Docker Desktop
- Una cuenta de Google Cloud con un Client ID de OAuth creado (ver sección Auth más abajo)

### 2. Base de datos

```bash
docker run --name misfinanzas-db -e POSTGRES_PASSWORD=devpass -e POSTGRES_DB=misfinanzas -p 5432:5432 -d postgres:16
docker cp schema_init.sql misfinanzas-db:/schema_init.sql
docker exec -it misfinanzas-db psql -U postgres -d misfinanzas -f /schema_init.sql
docker cp seed_categories.sql misfinanzas-db:/seed_categories.sql
docker exec -it misfinanzas-db psql -U postgres -d misfinanzas -f /seed_categories.sql
```

### 3. Configuración

En `src/main/resources/application.yml`, ajusta si hace falta:

```yaml
misfinanzas:
  security:
    jwt:
      secret: ${JWT_SECRET:cambia-este-valor-en-produccion-por-uno-largo-y-aleatorio}
    google:
      client-id: TU_CLIENT_ID.apps.googleusercontent.com
```

El Client ID de Google se crea en [Google Cloud Console](https://console.cloud.google.com) → APIs y servicios → Credenciales. No es un secreto (va incluido en el frontend), así que puede ir directamente en el YAML.

### 4. Arranca la aplicación

Desde el IDE, o por terminal con el wrapper de Maven:

```bash
./mvnw spring-boot:run        # Linux/Mac
.\mvnw.cmd spring-boot:run     # Windows PowerShell
```

La API queda disponible en `http://localhost:8080`.

### 5. Prueba el login

Sirve `test-login.html` con un servidor local en el puerto autorizado en Google Cloud Console:

```bash
python3 -m http.server 5173
```

Abre `http://localhost:5173/test-login.html`, inicia sesión, y usa el ID token que te da para probar `POST /api/auth/google`.

## Tests

```bash
./mvnw test
```

- Tests unitarios: `*ServiceTest` (JUnit 5 + Mockito, sin Spring)
- Tests de la capa web: `*ControllerTest` (`@WebMvcTest` + MockMvc)

## Endpoints principales

| Método | Ruta | Descripción | Auth |
|---|---|---|---|
| POST | `/api/auth/google` | Login con ID token de Google | No |
| POST | `/api/auth/refresh` | Renovar tokens | No |
| POST | `/api/auth/logout` | Cerrar sesión | No |
| GET/POST | `/api/transactions` | Listar / crear movimientos | Sí |
| GET | `/api/transactions/summary` | Resumen mensual (dashboard) | Sí |
| GET/POST | `/api/categories` | Listar / crear categorías | Sí |
| DELETE | `/api/categories/{id}` | Borrar categoría propia | Sí |
| GET/POST | `/api/households` | Listar / crear grupos compartidos | Sí |
| POST | `/api/households/{id}/members` | Añadir miembro (solo owner) | Sí |

Todas las rutas bajo `/api/**` excepto `/api/auth/**` requieren `Authorization: Bearer <access token>`.

## Documentación adicional

- [`docs/ARQUITECTURA_Y_TESTS.docx`](docs/ARQUITECTURA_Y_TESTS.docx) — por qué monolito modular, arquitectura hexagonal, cómo y por qué testeamos así.
- [`docs/DOCKER.docx`](docs/DOCKER.docx) — conceptos de Docker usados en el proyecto y comandos de referencia.
- `Informe_App_Finanzas.docx` / `Informe_Costes_App_Finanzas.docx` — informes iniciales de viabilidad y costes.

## Roadmap

- [x] Fase 1: CRUD de movimientos, categorías, autenticación, grupos compartidos, resumen mensual
- [ ] Frontend web (React, PWA)
- [ ] Import/export de Excel
- [ ] Tests de integración con Testcontainers
- [ ] Despliegue (Railway/Render + Vercel)
- [ ] Fase 2: informes con IA (Claude API)
- [ ] Fase 3: Open Banking (pospuesto)