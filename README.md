# Online Game

A two-player online board game: a Spring Boot 4 API, a React + TypeScript frontend, and an H2 database.

The game is the vehicle, not the goal. This project exists to get hands-on experience with:

1. **WebSockets**: real-time, bidirectional game state between server and clients
2. **Auth between client and server**: registration, login, sessions, and authorizing both HTTP and WebSocket traffic
3. **Common web security issues**: SQL injection, XSS, and CSRF, plus related issues (cross-site WebSocket hijacking, IDOR, cheating clients)

For each security topic the plan is to **see the attack work, then fix it, then write a test that proves the fix holds.**

---

## Project plan

### Which game

**Start with tic-tac-toe, design for chess.**

Tic-tac-toe keeps game rules to about 30 lines, so the time goes into WebSockets, auth, and security. To keep the door open for chess, game logic sits behind an interface from day one:

```java
public interface GameRules<S, M> {
    S initialState();
    MoveResult<S> apply(S state, M move, PlayerSide side); // validates and returns new state or an error
    GameOutcome outcome(S state);                          // IN_PROGRESS, WIN_X, WIN_O, DRAW, ...
}
```

Swapping in chess later means writing `ChessRules` (or wrapping a library such as `chesslib`) and a new board component. The networking, auth, matchmaking, and persistence code stays the same.

### Target architecture

```
 Browser (React)                                Spring Boot
 ───────────────                                ───────────
 Login / Register ── HTTPS (REST + cookie) ──▶ Spring Security (session, CSRF)
 Lobby            ── REST ───────────────────▶ GameController (create / join / list)
 Game board       ◀═ WebSocket (STOMP) ══════▶ GameMessageController
                                                  │
                                                  ▼
                                               GameService ── GameRules (tic-tac-toe, later chess)
                                                  │
                                                  ▼
                                               JPA ── H2 (users, games, moves)
```

Key decisions:

| Decision | Choice | Why |
|---|---|---|
| Auth mechanism | **Server-side session + `HttpOnly` cookie** (Spring Security) | Browser-based app, so cookies are the natural fit. Cookies are also what make CSRF a real threat, which is the point of the exercise. A JWT stage can be added later for comparison (see Phase 9). |
| Password storage | `BCryptPasswordEncoder` (or Argon2) | Never store or log plaintext passwords. |
| WebSocket protocol | **STOMP over WebSocket** (`spring-boot-starter-websocket`, `@stomp/stompjs` on the client) | Gives topics/subscriptions and integrates with Spring Security's message-level authorization. Optionally build a raw `TextWebSocketHandler` spike first to see what STOMP abstracts away. |
| Source of truth | **Server-authoritative** | Clients send *intents* ("place X at 4"); the server validates turn, legality, and membership, then broadcasts the new state. The client never decides who won. |
| Persistence | JPA entities `User`, `Game`, `Move` | Moves are stored so a game can be reloaded after refresh or reconnect. |

### Phases

Each phase should end with working code, tests, and a short note in this README about what was learned.

Spring Security, Spring's WebSocket support, React, and Hibernate handle much of the security in this plan without showing you. [security-internals.md](docs/security-internals.md) lists what each one does behind the scenes, how to make it visible, and exercises for building small versions yourself. Read the matching section before starting each security phase.

#### Phase 0: Clean up the template
- [x] Upgrade to current releases: Java 25 (LTS), Spring Boot 4.2 (Spring Framework 7, Spring Security 7, Hibernate 7, Jackson 3), Node 26 (LTS), React 19, Vite 8, Vitest 5, TypeScript 7
- [ ] Remove the `Idea` feature (entity, repository, service, controller, frontend components) or keep it briefly as a reference
- [x] Rename the project from its template name: package `com.brian.onlinegame`, main class `OnlineGameApplication`, Maven artifact and npm packages `online-game`
- [ ] Turn off verbose error output that leaks internals: `server.error.include-message` and `include-binding-errors` (keep the custom `GlobalExceptionHandler` controlling responses)
- [ ] Plan to disable or lock down the H2 console once Spring Security is added (it is an unauthenticated SQL shell)

#### Phase 1: Local game, no networking
- [ ] `GameRules` interface + `TicTacToeRules` with full unit tests (wins, draws, illegal moves, moving out of turn)
- [ ] React board component that plays a local hot-seat game against itself
- **Learning goal:** a clean, framework-free game core that the rest of the system calls into

#### Phase 2: Users and authentication
- [ ] Add `spring-boot-starter-security`
- [ ] `User` entity (username, password hash, created at) and `UserRepository`
- [ ] `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me`
- [ ] Session cookie settings: `HttpOnly`, `SameSite=Lax`, `Secure` (when on HTTPS)
- [ ] For a JSON login endpoint (instead of Spring's built-in form login), save the `SecurityContext` to the session yourself. Since Spring Security 6 this is no longer automatic, and skipping it makes login appear to work while the next request comes back unauthenticated. See [security-internals.md](docs/security-internals.md#2-authentication-flow).
- [ ] Change the session ID on login (Spring's session fixation protection, verify it is on)
- [ ] Generic login error messages ("invalid username or password") so usernames cannot be enumerated
- [ ] Basic rate limiting or lockout on login attempts
- [ ] Frontend: login/register pages, an auth context (`useAuth`) driven by `/api/auth/me`, protected routes
- [ ] Update `frontend/src/api/http.ts` to send `credentials: 'include'` and handle 401 by redirecting to login
- **Learning goal:** how a session is created, carried by the browser, and checked on every request

#### Phase 3: CSRF
- [ ] **Set up a real attacker site.** Browsers decide "same-site" by scheme and registrable domain and **ignore the port**, so a page on `localhost:6000` is the *same site* as the app on `localhost:5173`. `SameSite` cookies do nothing against it, which can make it look as though `SameSite` never works. Serve the evil page from a different hostname instead:
  - quickest: open the app at `http://localhost:5173` and serve the evil page at `http://127.0.0.1:6000` (`localhost` and `127.0.0.1` are different sites)
  - more realistic: add `127.0.0.1 evil.test` to your hosts file (`C:\Windows\System32\drivers\etc\hosts`) and serve the page at `http://evil.test:6000`
- [ ] **See it break:** temporarily disable CSRF protection *and* set the session cookie to `SameSite=None` (this requires `Secure`, which Chrome allows on `http://localhost`). Have the evil page auto-submit a form to `POST /api/games` or `/api/auth/logout`, and confirm it succeeds using the victim's cookie.
- [ ] **See `SameSite` alone:** switch the cookie back to `SameSite=Lax` with CSRF still disabled, and confirm the cross-site form POST now arrives *without* the cookie. Then try the same attack from a same-site page (`localhost:6000`) and watch it succeed again. That is the scenario `SameSite` does not cover, for example a compromised sibling subdomain.
- [ ] **Fix it:** enable Spring Security CSRF for a single-page app (`csrf.spa()`, or `CookieCsrfTokenRepository.withHttpOnlyFalse()` plus a request handler, configured by hand). The frontend reads the `XSRF-TOKEN` cookie and sends it back as the `X-XSRF-TOKEN` header on every state-changing request (`http.ts` is the single place to add this).
- [ ] Write down why `SameSite=Lax` helps but does not replace tokens: it fails same-site attackers, and older or unusual browsers apply it differently
- [ ] Make sure no state changes happen on `GET`
- [ ] Tests: `MockMvc` requests without a CSRF token get 403; requests `.with(csrf())` succeed
- **Learning goal:** why cookie-based auth needs CSRF defenses, and how the synchronizer/double-submit token pattern works

#### Phase 4: Lobby and matchmaking (REST)
- [ ] `Game` entity: id, player X, player O, status (`WAITING`, `IN_PROGRESS`, `FINISHED`), board state, whose turn, winner
- [ ] `POST /api/games` (create), `GET /api/games?status=WAITING` (list open games), `POST /api/games/{id}/join`, `GET /api/games/{id}`
- [ ] **Authorization (IDOR):** a user can only act on games they belong to. Test that user C gets 403 on a game between A and B.
- [ ] Use random UUIDs for game IDs rather than sequential numbers
- [ ] Frontend lobby page: list open games, create, join
- **Learning goal:** authentication (who are you) vs. authorization (what are you allowed to touch)

#### Phase 5: Real-time play over WebSockets
- [ ] Add `spring-boot-starter-websocket`; configure a STOMP endpoint at `/ws` with a simple broker
- [ ] Destinations:
  - Client sends moves to `/app/games/{id}/move`
  - Server broadcasts state to `/topic/games/{id}`
  - Server sends private errors to `/user/queue/errors`
- [ ] The WebSocket handshake reuses the HTTP session cookie, so the `Principal` is available in message handlers
- [ ] Message-level security (`@EnableWebSocketSecurity` / `AuthorizationManager<Message<?>>`): require auth for all messages, and check game membership on both `SEND` and `SUBSCRIBE`
- [ ] Server-side move validation: right player, right turn, legal square, game in progress. Never trust the client's board.
- [ ] Handle reconnects: on (re)subscribe, the client fetches current state via `GET /api/games/{id}`
- [ ] Handle disconnects: listen for `SessionDisconnectEvent`; decide on a forfeit timeout or just mark the player as away
- [ ] Vite: add `ws: true` to a `/ws` proxy entry in `frontend/vite.config.ts`
- [ ] Frontend: `useGameSocket(gameId)` hook wrapping `@stomp/stompjs`, with connection status shown in the UI
- [ ] Tests: integration test with a `WebSocketStompClient` playing a full game; a test that a non-member cannot subscribe or send moves
- **Learning goal:** WebSocket lifecycle (handshake, frames, heartbeats, disconnects), pub/sub, and why authorization must be enforced per message, not just at connect time

#### Phase 6: WebSocket-specific security
- [ ] **Cross-Site WebSocket Hijacking (CSWSH):** the same-origin policy does not apply to WebSockets, so any page can open a socket to your server, and the browser attaches cookies to the handshake. Build an evil page that opens a socket to `/ws` and reads game state. Two things decide whether it works:
  - **The Origin check:** Spring only allows same-origin handshakes by default, so to see the attack you first have to loosen it (for example `setAllowedOriginPatterns("*")`). Ports *do* count for origins, so `localhost:6000` is a different origin.
  - **`SameSite`:** a `Lax` session cookie is not sent on a cross-site handshake (`127.0.0.1` or `evil.test`) but *is* sent on a same-site one (`localhost:6000`). Try both.
  - **Fix it:** use `setAllowedOriginPatterns(...)` with only the real frontend origin, and add a test that a handshake from any other `Origin` header is rejected.
- [ ] Spring's STOMP CSRF: by default Spring Security requires a CSRF token in the STOMP `CONNECT` frame headers. Send it from the client rather than disabling the check.
- [ ] Limit message size and send rate (`WebSocketTransportRegistration`) so a client cannot flood the server
- **Learning goal:** WebSockets have their own security model, and HTTP protections do not automatically carry over

#### Phase 7: XSS (via in-game chat and usernames)
- [ ] Add a per-game chat over the same STOMP connection; usernames are also displayed everywhere. Both are user-controlled strings rendered to other users.
- [ ] **See it break:** render a chat message with `dangerouslySetInnerHTML` (on a throwaway branch) and send `<img src=x onerror=alert(document.cookie)>`. Note that the `HttpOnly` session cookie is *not* readable, which shows why that flag matters, but the attacker can still make requests as the victim.
- [ ] **Fix it:** render as plain text (React escapes by default); never use `dangerouslySetInnerHTML` on user input; validate usernames to a strict pattern (e.g. `^[a-zA-Z0-9_]{3,20}$`)
- [ ] Watch for other sinks: `href={userValue}` (`javascript:` URLs), `innerHTML` in non-React code, error messages echoed from the server
- [ ] Add security headers: a strict `Content-Security-Policy` (no inline scripts), `X-Content-Type-Options: nosniff`, `frame-ancestors 'none'` (clickjacking)
- [ ] Tests: a component test that renders a malicious chat message and asserts it appears as text with no element injected
- **Learning goal:** stored XSS, output encoding vs. input validation, and CSP as defense in depth

#### Phase 8: SQL injection
- [ ] **First, switch H2 to Postgres in Docker.** Add a `compose.yaml` with a `postgres` service, swap the `h2` dependency for `org.postgresql:postgresql`, and update `spring.datasource.*`. Postgres behaves more like a real target, and the least-privilege step below only means something with real database roles. Spring Boot's Docker Compose support (`spring-boot-docker-compose`) can start the container for you on `mvn spring-boot:run`.
- [ ] Add a feature that needs a query with user input, such as player search (`GET /api/users?search=...`) or match history filtering and sorting
- [ ] **See it break:** on a throwaway branch, build the query with string concatenation through `JdbcTemplate` or `EntityManager.createNativeQuery("... WHERE username LIKE '%" + search + "%'")`. Exploit it with `' OR '1'='1` and a `UNION SELECT` that dumps password hashes.
- [ ] **Fix it:** parameterized queries (Spring Data derived queries, `@Query` with named parameters, or `?` placeholders in `JdbcTemplate`)
- [ ] The subtle case: **`ORDER BY` columns cannot be parameterized.** Map a user-supplied sort key to an allowlist of column names.
- [ ] Least privilege: create a Postgres role for the app that can `SELECT`/`INSERT`/`UPDATE`/`DELETE` but not `DROP` or `ALTER`, and keep schema changes under a separate admin role (a natural point to introduce Flyway migrations instead of `ddl-auto=update`). Re-run the injection payloads and see what the attacker can and cannot do now.
- [ ] Tests: a repository/controller test that passes injection payloads and asserts they are treated as literal strings
- **Learning goal:** why JPA protects you by default, and the specific places where it stops protecting you

#### Phase 9 (optional): Stretch goals
- [ ] **JWT comparison:** add a second auth mode with a short-lived access token in memory and a refresh token in an `HttpOnly` cookie. Compare the CSRF and XSS trade-offs against sessions.
- [ ] Swap tic-tac-toe for chess via the `GameRules` interface
- [ ] Spectator mode (read-only subscriptions) and a game clock
- [ ] Run [OWASP ZAP](https://www.zaproxy.org/) against the app and fix what it finds
- [ ] Deploy behind HTTPS (cookies with `Secure`, HSTS header)
- [ ] Scale-out thinking: what breaks with two server instances (in-memory broker, sessions) and how a relay broker or Spring Session + Redis fixes it

### Security checklist

A running checklist to confirm before calling the project done:

| Area | Check |
|---|---|
| Passwords | Hashed with BCrypt/Argon2; never logged; minimum length enforced |
| Session | `HttpOnly`, `SameSite`, `Secure` (on HTTPS); ID rotated on login; invalidated on logout |
| CSRF | Token required on all state-changing HTTP requests and on STOMP `CONNECT` |
| WebSocket | Origin allowlist; auth required for all frames; membership checked on `SEND` and `SUBSCRIBE`; size/rate limits |
| Authorization | Every game endpoint and message checks the current user is a participant |
| Game integrity | Server validates every move; client state is display only |
| XSS | No `dangerouslySetInnerHTML` on user data; strict username validation; CSP header set |
| SQLi | No string-built SQL; sort/filter columns allowlisted |
| Errors | No stack traces or internal messages in responses |
| Dev tools | H2 console disabled or restricted outside local dev |

### Learning log

Add a short entry here as each phase is completed: what was built, what surprised you, and links to the relevant commits.

---

## Development setup

The sections below describe the stack inherited from the original template. They still apply, and will be updated as the `Idea` feature is replaced by the game.

- Backend: Java 25, Spring Boot 4.2, Spring Web MVC, Spring Data JPA (Hibernate 7), Bean Validation
- Frontend: React 19, TypeScript 7 (strict), Vite 8, Vitest 5
- Database: H2, stored in a local file (no database install needed)

More detailed guides are in [docs/](docs/):

- [backend.md](docs/backend.md): backend layout, entry points, and how each piece is wired to Spring
- [frontend.md](docs/frontend.md): frontend layout, entry points, and tooling
- [security-internals.md](docs/security-internals.md): what the frameworks do for security behind the scenes, and exercises for learning it

## Prerequisites

Install these first and confirm each version in a terminal.

| Tool | Version | Check | Notes |
|---|---|---|---|
| JDK | 25 (current LTS) | `java -version` | Any distribution (Temurin, Corretto, etc.) |
| Maven | 3.9+ | `mvn -version` | Needed on your PATH for `npm run dev`. Not needed if you run the backend from IntelliJ. |
| Node.js | 26 (current LTS; 22.22+ or 24.15+ also work) | `node -v` and `npm -v` | npm ships with Node |
| Git | any recent | `git --version` | |

`JAVA_HOME` should point at the JDK 25 install so Maven uses the right Java.

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

or open the project folder in IntelliJ (File > Open, choose the folder with `pom.xml`), let Maven finish importing, then run `OnlineGameApplication` with the green arrow next to `main`.

### Frontend (port 5173)

```bash
cd frontend
npm install
npm run dev
```

Vite proxies any request starting with `/api` to the backend at `http://127.0.0.1:8080`, so no CORS setup is needed. Always call the API with relative URLs (for example `/api/ideas`).

## Verify it works

1. Open http://localhost:5173.
2. Add an idea with a title. It should appear in the list.
3. Vote on an idea. Its vote count should go up.
4. Refresh the page. Your ideas should still be there, which confirms they are stored in the database.

You can also hit the API directly:

- `GET http://localhost:8080/api/ideas` returns all ideas as JSON
- `POST http://localhost:8080/api/ideas` creates an idea (`Content-Type: application/json`, body `{"title":"x","description":""}`)
- `POST http://localhost:8080/api/ideas/{id}/vote` adds a vote to an idea

## Database

H2 runs inside the backend process and stores data in `./data/onlinegame` (created on first run, ignored by git).

- Console: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:file:./data/onlinegame`
- User: `sa`, password: blank

To reset the data, stop the backend and delete the `data/` folder.

To use an in-memory database instead (data resets on every restart), set this in `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:h2:mem:onlinegame;DB_CLOSE_DELAY=-1
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
│   ├── java/com/brian/onlinegame/
│   │   ├── OnlineGameApplication.java       Backend entry point
│   │   ├── model/Idea.java                    JPA entity + validation rules
│   │   ├── repository/IdeaRepository.java     Spring Data repository
│   │   ├── service/IdeaService.java           Business logic (create, vote)
│   │   └── controller/IdeaApiController.java  REST endpoints (/api/ideas)
│   └── resources/application.properties
└── frontend/
    ├── index.html               Browser entry
    ├── vite.config.ts           Dev server and /api proxy
    ├── tsconfig.json
    └── src/
        ├── main.tsx             Mounts React
        ├── App.tsx              Page shell
        ├── api/                 http.ts (fetch helper), ideaApi.ts (backend calls)
        ├── component/           IdeaView (form + state), IdeaList, IdeaCard
        ├── types/ideaTypes.ts   Types mirroring the backend entity
        └── styles.css
```

When you change the `Idea` entity, update `frontend/src/types/ideaTypes.ts` to match.

## Useful commands

| Command | Where | Purpose |
|---|---|---|
| `npm run setup` | root | Install root and frontend dependencies |
| `npm run dev` | root | Run backend and frontend together |
| `mvn spring-boot:run` | root | Run only the backend |
| `mvn test` | root | Run backend tests |
| `npm run dev` | `frontend/` | Run only the frontend |
| `npm test` | `frontend/` | Run frontend unit tests once (`npm run test:watch` to re-run on save) |
| `npm run build` | `frontend/` | Type-check and build to `frontend/dist/` |

## Troubleshooting

**Annotations or imports show red in IntelliJ.** Maven has not finished importing. Open the Maven tool window and click "Reload All Maven Projects", and make sure the project SDK is 25 (File > Project Structure).

**`mvn` is not recognized.** Install Maven and add its `bin` folder to your PATH, or run the backend from IntelliJ and start the frontend with `cd frontend && npm run dev`.

**Build fails with an "invalid target release" error.** Maven is using an older JDK. Check `java -version` and `JAVA_HOME`.

**Port 8080 or 5173 is already in use.** Stop the other process using that port. To change the backend port, set `server.port` in `application.properties` and update the proxy target in `frontend/vite.config.ts`.

**Frontend loads but the list is empty and requests fail.** The backend is not running. Start it and refresh.

**Occasional 2 second delay on the first request after a pause.** This is a local connection-setup delay between the browser and the dev server. Keeping the proxy target as `127.0.0.1` (not `localhost`) in `frontend/vite.config.ts` avoids IPv6 lookup delays on some machines.

**Backend fails at startup with an H2 database format error after pulling the upgrade.** H2 moved from 2.2 to 2.5, and newer H2 versions may not open database files written by older ones. Stop the backend and delete the `data/` folder (it only holds local dev data).

**`npm run setup` fails on an old Node version.** Upgrade to Node 26 (the current LTS). Vite 8, Vitest 5, and jsdom 31 require Node 22.22+ at minimum.