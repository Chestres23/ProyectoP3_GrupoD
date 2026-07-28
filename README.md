# CampusLost - Proyecto P3

Repositorio del proyecto integrador de la materia. El backend usa Spring Boot reactivo con WebFlux y el frontend usa Vite + React + Tailwind.

## Ejecución local

Backend:

```bash
cd ProyectoP3_GrupoD/backend
.\gradlew test
.\gradlew bootRun
```

Frontend:

```bash
cd ProyectoP3_GrupoD/frontend
npm install
npm run dev
```

## Pruebas y CI

El backend incluye pruebas unitarias reactiva/clásicas con Mockito y StepVerifier. El flujo de GitHub Actions corre build y tests para backend y frontend en la rama `main` y el CD publica el backend en Render.

## Despliegue

El frontend sí puede desplegarse en Vercel. Define `VITE_API_URL` apuntando al backend publicado y mantén el archivo `frontend/vercel.json` para soportar rutas SPA.

El backend Spring Boot no es un objetivo natural de Vercel como servicio persistente. Para esa parte es mejor usar Render, Railway, Fly.io o un contenedor en otro proveedor. Si quieres exponerlo a un frontend en Vercel, configura también `APP_CORS_ALLOWED_ORIGINS` con el dominio del frontend.

## Notas

- El backend consume la API bajo `/api`.
- Para probar login usa los usuarios sembrados por backend, por ejemplo `admin@test.com` / `123` o `user@test.com` / `123`.
- El módulo principal sigue siendo Claims, con soporte para objetos perdidos y monitoreo reactivo.
