# Guía de defensa - Proyecto P3

Este documento resume qué se aplicó en el Proyecto P3, en qué parte del proyecto está implementado y cómo explicarlo en una defensa oral.

## 1. Visión general del proyecto

El proyecto P3 es **CampusLost**, un sistema para gestionar objetos perdidos y reclamos.

La solución está dividida en dos partes principales:
- **Backend**: Spring Boot reactivo con WebFlux y R2DBC.
- **Frontend**: Vite + React + Tailwind.

Además, el proyecto incluye:
- Pruebas unitarias con JUnit 5, Mockito y StepVerifier.
- Integración continua con GitHub Actions.
- Despliegue del frontend en Vercel.
- Despliegue del backend en un servicio externo tipo Render.
- Cobertura adicional de servicios clave como autenticación, usuarios y objetos perdidos.

## 2. Qué se aplicó del laboratorio 5 y 6

### 2.1 Patrón de dependencias mockeadas
Se aplicó la idea de separar la lógica del negocio de sus dependencias para poder probarla sin tocar base de datos real ni servicios externos.

En el proyecto esto se refleja en:
- [backend/src/main/java/ec/edu/espe/backend/service/impl/ClaimServiceImpl.java](backend/src/main/java/ec/edu/espe/backend/service/impl/ClaimServiceImpl.java)
- [backend/src/main/java/ec/edu/espe/backend/reactive/service/ReactiveClaimService.java](backend/src/main/java/ec/edu/espe/backend/reactive/service/ReactiveClaimService.java)

Aunque el dominio no usa `WalletRepository`, `RiskClient`, `OrderRepository` o `FraudClient` con esos nombres, sí usa el mismo concepto:
- Repositorios como dependencias inyectadas.
- Servicios externos simulables en pruebas.
- Lógica validada antes de persistir o emitir eventos.

### 2.2 Validaciones de negocio
En los labs se validaban cosas como email, monto positivo, usuario bloqueado y datos duplicados.

En P3 eso se refleja en:
- Validaciones de reclamos en `ClaimServiceImpl`.
- Validaciones de estado en `approve`, `reject` y `deleteClaim`.
- Validaciones de datos de objetos y usuarios en los controladores y servicios relacionados.

### 2.3 Pruebas unitarias con AAA
Se aplicó el patrón **Arrange - Act - Assert** en las pruebas del backend.

Se ve en:
- [backend/src/test/java/ec/edu/espe/backend/service/ClaimServiceTest.java](backend/src/test/java/ec/edu/espe/backend/service/ClaimServiceTest.java)
- [backend/src/test/java/ec/edu/espe/backend/service/LostItemAuditServiceTest.java](backend/src/test/java/ec/edu/espe/backend/service/LostItemAuditServiceTest.java)
- [backend/src/test/java/ec/edu/espe/backend/reactive/service/ReactiveClaimServiceTest.java](backend/src/test/java/ec/edu/espe/backend/reactive/service/ReactiveClaimServiceTest.java)

Ahí se usan:
- `@BeforeEach` para preparar mocks.
- `Mockito.when(...)` para simular respuestas.
- `verify(...)` y `verifyNoInteractions(...)` para revisar interacciones.
- `ArgumentCaptor` e `InOrder` para validar datos y orden de llamadas.
- `StepVerifier` para probar flujos reactivos.

### 2.4 Cobertura adicional de funcionalidades
Para que el proyecto no quede limitado sólo a reclamos, también se agregaron pruebas que cubren el comportamiento principal del sistema:

- [backend/src/test/java/ec/edu/espe/backend/service/AuthServiceTest.java](backend/src/test/java/ec/edu/espe/backend/service/AuthServiceTest.java)
- [backend/src/test/java/ec/edu/espe/backend/service/UserServiceImplTest.java](backend/src/test/java/ec/edu/espe/backend/service/UserServiceImplTest.java)
- [backend/src/test/java/ec/edu/espe/backend/service/LostItemServiceTest.java](backend/src/test/java/ec/edu/espe/backend/service/LostItemServiceTest.java)

Con estas pruebas se verifica:
- Registro de usuario con token JWT.
- Login con credenciales válidas e inválidas.
- Guardado de usuarios únicos y desactivación lógica.
- Creación, listado, reclamo, entrega y validación de objetos perdidos.
- Rechazo de operaciones no permitidas por estado o por usuario no autorizado.

## 3. Qué hay en cada parte del proyecto

### 3.1 Backend
Ubicación principal:
- [backend/src/main/java/ec/edu/espe/backend](backend/src/main/java/ec/edu/espe/backend)

Contenido importante:
- `controller/`: endpoints REST y reactivos.
- `service/` y `service/impl/`: reglas de negocio.
- `repository/`: acceso a datos con R2DBC.
- `domain/`: entidades del sistema.
- `dto/`: objetos de transferencia.
- `reactive/`: lógica con SSE, estadísticas y simulación.
- `security/`: autenticación y configuración de seguridad.
- `config/`: carga inicial y filtros de logging.

Qué puedes decir en defensa:
- El backend no usa MVC tradicional, sino **WebFlux reactivo**.
- Las consultas y respuestas se manejan con `Mono` y `Flux`.
- La lógica de reclamos está desacoplada en servicios y repositorios.
- La autenticación se maneja con JWT y validaciones reactivas.

### 3.2 Frontend
Ubicación principal:
- [frontend/src](frontend/src)

Contenido importante:
- `pages/`: pantallas principales del sistema.
- `components/`: elementos reutilizables como rutas protegidas y sidebar.
- `services/`: consumo de la API.
- `layouts/`: layout principal de la aplicación.

Qué puedes decir en defensa:
- El frontend está hecho con Vite + React.
- Consume la API del backend mediante un cliente centralizado en `services/api.js`.
- Tiene rutas protegidas y vistas para login, dashboard, objetos, reclamos y monitor reactivo.

## 4. Qué se agregó para despliegue

### 4.1 GitHub Actions
Se revisó y dejó coherente el flujo de CI/CD.

Archivos:
- [`.github/workflows/ci.yml`](.github/workflows/ci.yml)
- [`.github/workflows/cd.yml`](.github/workflows/cd.yml)
- [`.github/workflows/rollback.yml`](.github/workflows/rollback.yml)

Qué hacen:
- `ci.yml`: compila y prueba backend y frontend.
- `cd.yml`: publica el backend en Render cuando el pipeline de CI termina bien.
- `rollback.yml`: intenta revertir si el despliegue falla.

### 4.2 Vercel
Se agregó soporte para frontend en Vercel:
- [frontend/vercel.json](frontend/vercel.json)

Eso sirve para que las rutas SPA funcionen al recargar una página interna.

### 4.3 CORS y variables de entorno
Se ajustó el backend para permitir dominios externos configurables:
- [backend/src/main/java/ec/edu/espe/backend/security/SecurityConfig.java](backend/src/main/java/ec/edu/espe/backend/security/SecurityConfig.java)
- [backend/src/main/resources/application.yml](backend/src/main/resources/application.yml)

Se centralizó la URL base de la API en frontend:
- [frontend/src/services/api.js](frontend/src/services/api.js)
- [frontend/src/services/itemApi.js](frontend/src/services/itemApi.js)

Esto permite cambiar la URL sin tocar el código principal.

## 5. Cómo defenderlo en exposición

### 5.1 Si te preguntan qué aprendiste de los labs
Puedes responder:
- Aprendí a separar lógica de negocio, dependencias y pruebas.
- Aprendí a usar mocks para probar sin depender de BD o servicios externos.
- Aprendí a verificar interacciones, orden de llamadas y casos negativos.
- Aprendí a integrar pruebas, build y despliegue en CI/CD.

### 5.2 Si te preguntan qué se copió al proyecto y qué se adaptó
Puedes responder:
- No se copiaron literalmente los servicios `OrderService` o `WalletService` porque el proyecto usa otro dominio.
- Sí se adaptó el mismo patrón de trabajo a `ClaimService` y al flujo reactivo del sistema.
- El objetivo fue aplicar la metodología de los labs dentro del proyecto real.

### 5.3 Si te preguntan por qué no está Wallet u Order en P3
Puedes responder:
- Porque el proyecto P3 trata sobre objetos perdidos y reclamos, no sobre billeteras u órdenes.
- La lógica de los labs se usó como referencia metodológica, no como dominio final.

## 6. Resumen rápido para decir en una defensa

> En el proyecto P3 se implementó un sistema CampusLost con backend reactivo en Spring Boot y frontend en React. Se aplicaron pruebas unitarias con Mockito, StepVerifier y patrón AAA, además de CI/CD con GitHub Actions. También se preparó el frontend para Vercel y el backend para despliegue externo con CORS configurable. Todo el trabajo de los laboratorios se adaptó al dominio real del proyecto, especialmente en la parte de reclamos, servicios y pruebas.

## 7. Archivos más importantes para mostrar

- [backend/src/main/java/ec/edu/espe/backend/service/impl/ClaimServiceImpl.java](backend/src/main/java/ec/edu/espe/backend/service/impl/ClaimServiceImpl.java)
- [backend/src/main/java/ec/edu/espe/backend/service/impl/AuthService.java](backend/src/main/java/ec/edu/espe/backend/service/impl/AuthService.java)
- [backend/src/main/java/ec/edu/espe/backend/service/impl/LostItemServiceImpl.java](backend/src/main/java/ec/edu/espe/backend/service/impl/LostItemServiceImpl.java)
- [backend/src/test/java/ec/edu/espe/backend/service/ClaimServiceTest.java](backend/src/test/java/ec/edu/espe/backend/service/ClaimServiceTest.java)
- [backend/src/test/java/ec/edu/espe/backend/reactive/service/ReactiveClaimServiceTest.java](backend/src/test/java/ec/edu/espe/backend/reactive/service/ReactiveClaimServiceTest.java)
- [backend/src/test/java/ec/edu/espe/backend/service/AuthServiceTest.java](backend/src/test/java/ec/edu/espe/backend/service/AuthServiceTest.java)
- [backend/src/test/java/ec/edu/espe/backend/service/UserServiceImplTest.java](backend/src/test/java/ec/edu/espe/backend/service/UserServiceImplTest.java)
- [backend/src/test/java/ec/edu/espe/backend/service/LostItemServiceTest.java](backend/src/test/java/ec/edu/espe/backend/service/LostItemServiceTest.java)
- [frontend/src/pages/ClaimsPanel.jsx](frontend/src/pages/ClaimsPanel.jsx)
- [frontend/src/pages/ReactiveMonitor.jsx](frontend/src/pages/ReactiveMonitor.jsx)
- [frontend/src/services/api.js](frontend/src/services/api.js)
- [frontend/vercel.json](frontend/vercel.json)
- [`.github/workflows/ci.yml`](.github/workflows/ci.yml)

## 8. Idea final

Si quieres defender bien, enfócate en esta idea:
- Los laboratorios te enseñaron la técnica.
- El proyecto P3 demuestra la aplicación real de esa técnica en un sistema completo.
