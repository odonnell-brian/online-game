# Interview Starter

A small full-stack starter: a **Spring Boot 3** JSON API with a **React + TypeScript** frontend (built with Vite) and an **H2** file database.

- Backend: Java 17, Spring Boot 3.3, Spring Web, Spring Data JPA, Bean Validation
- Frontend: React 18, TypeScript (strict), Vite 5
- Database: H2, stored in a local file (no database install needed)

## Prerequisites

Install these first and confirm each version in a terminal.

| Tool | Version | Check | Notes |
|---|---|---|---|
| JDK | 17 or newer | `java -version` | Any distribution (Temurin, Corretto, etc.) |
| Maven | 3.9+ | `mvn -version` | Needed on your PATH for `npm run dev`. Not needed if you run the backend from IntelliJ. |
| Node.js | 18 or newer (LTS recommended) | `node -v` and `npm -v` | npm ships with Node |
| Git | any recent | `git --version` | |

`JAVA_HOME` should point at the JDK 17+ install so Maven uses the right Java.

## Quick start (one command)

From the project root (the folder containing `pom.xml`):

```bash
npm run setup    # once: installs root and frontend dependencies
npm run dev      # starts the backend (8080) and frontend (5173) together
```

Then open **http://localhost:5173**.

Output from both processes appears in one terminal, labeled `api` and `web`. Stop both with Ctrl+C.

## Running the pieces separately

### Backend (port 8080)

Either use Maven:

```bash
mvn spring-boot:run
```

or open the project folder in IntelliJ (File > Open, choose the folder with `pom.xml`), let Maven finish importing, then run `InterviewApplication` with the green arrow next to `main`.

### Frontend (port 5173)

```bash
cd frontend
npm install
npm run dev
```

Vite proxies any request starting with `/api` to the backend at `http://127.0.0.1:8080`, so no CORS setup is needed. Always call the API with relative URLs (for example `/api/items`).

## Verify it works

1. Open http://localhost:5173.
2. Add an item with a title. It should appear in the list.
3. Submit with an empty title. A validation error should show.
4. Refresh the page. Your items should still be there, which confirms they are stored in the database.

You can also hit the API directly:

- `GET http://localhost:8080/api/items` returns all items as JSON
- `POST http://localhost:8080/api/items` creates an item (`Content-Type: application/json`, body `{"title":"x","description":""}`)

## Database

H2 runs inside the backend process and stores data in `./data/interview` (created on first run, ignored by git).

- Console: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:file:./data/interview`
- User: `sa`, password: blank

To reset the data, stop the backend and delete the `data/` folder.

To use an in-memory database instead (data resets on every restart), set this in `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:h2:mem:interview;DB_CLOSE_DELAY=-1
```

### Switching to Postgres (optional)

1. In `pom.xml`, replace the `h2` dependency with `org.postgresql:postgresql`.
2. Update the `spring.datasource.*` lines in `application.properties`.
3. Start a local database, for example: `docker run -e POSTGRES_PASSWORD=pw -p 5432:5432 postgres`.

## Project structure

```
.
├── pom.xml                      Maven build and backend dependencies
├── package.json                 Root scripts: setup and dev
├── src/main/
│   ├── java/com/example/interview/
│   │   ├── InterviewApplication.java   Backend entry point
│   │   ├── Item.java                   JPA entity + validation rules
│   │   ├── ItemRepository.java         Spring Data repository
│   │   └── ItemApiController.java      REST endpoints (/api/items)
│   └── resources/application.properties
└── frontend/
    ├── index.html               Browser entry
    ├── vite.config.ts           Dev server and /api proxy
    ├── tsconfig.json
    └── src/
        ├── main.tsx             Mounts React
        ├── App.tsx              Form, list, and state
        ├── api.ts               All calls to the backend
        ├── types.ts             Types mirroring backend entities
        └── styles.css
```

When you change the `Item` entity, update `frontend/src/types.ts` to match.

## Useful commands

| Command | Where | Purpose |
|---|---|---|
| `npm run setup` | root | Install root and frontend dependencies |
| `npm run dev` | root | Run backend and frontend together |
| `mvn spring-boot:run` | root | Run only the backend |
| `mvn test` | root | Run backend tests |
| `npm run dev` | `frontend/` | Run only the frontend |
| `npm run build` | `frontend/` | Type-check and build to `frontend/dist/` |

## Troubleshooting

**Annotations or imports show red in IntelliJ.** Maven has not finished importing. Open the Maven tool window and click "Reload All Maven Projects", and make sure the project SDK is 17 or newer (File > Project Structure).

**`mvn` is not recognized.** Install Maven and add its `bin` folder to your PATH, or run the backend from IntelliJ and start the frontend with `cd frontend && npm run dev`.

**Build fails with an "invalid target release" error.** Maven is using an older JDK. Check `java -version` and `JAVA_HOME`.

**Port 8080 or 5173 is already in use.** Stop the other process using that port. To change the backend port, set `server.port` in `application.properties` and update the proxy target in `frontend/vite.config.ts`.

**Frontend loads but the list is empty and requests fail.** The backend is not running. Start it and refresh.

**Occasional 2 second delay on the first request after a pause.** This is a local connection-setup delay between the browser and the dev server. Keeping the proxy target as `127.0.0.1` (not `localhost`) in `frontend/vite.config.ts` avoids IPv6 lookup delays on some machines.

**`npm run setup` fails on an old Node version.** Upgrade to Node 18 or newer.