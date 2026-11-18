# Interview Starter (Spring Boot 3 API + React/Vite + H2)

Requires JDK 17+, Maven, Node 18+. Frontend is TypeScript.

## Run (one command)

    npm run setup     # once: installs root + frontend deps
    npm run dev       # starts Spring Boot (8080) and Vite (5173) together

Open http://localhost:5173. Vite proxies /api to Spring Boot, so there is no CORS setup.

Prefer IntelliJ for the backend? Run InterviewApplication there and just `cd frontend && npm run dev`.

Type-check the frontend with `cd frontend && npm run build`.

- API: GET/POST http://localhost:8080/api/items
- DB console: http://localhost:8080/h2-console (JDBC URL: jdbc:h2:file:./data/interview, user sa, blank password)

## Swap H2 for Postgres (optional)
1. Replace the h2 dependency in pom.xml with org.postgresql:postgresql
2. Update the spring.datasource.* lines in application.properties
   (e.g. `docker run -e POSTGRES_PASSWORD=pw -p 5432:5432 postgres`)

## Where to go from here
Rename Item to the real domain object, add a Service layer once there is logic,
split App.tsx into components as the UI grows.
