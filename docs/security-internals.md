# What the Frameworks Hide

Spring Security, Spring WebSocket, React, and Hibernate each handle a security problem in this project without you writing the protection yourself. That is good for shipping software and bad for learning: you can finish every phase in the [README](../README.md) and still only know which settings to flip.

This guide lists what each framework does for you behind the scenes, how to make it visible, and a small exercise that makes you build or break it yourself. Read the matching section before each security phase.

## The method

For each protection, work through five steps:

1. **Observe.** Watch the real traffic and logs before changing anything. Find the cookie, header, or frame the protection depends on.
2. **Break.** Turn the protection off and carry out the attack. If you cannot make the attack work, you do not yet understand what the protection stops.
3. **Rebuild.** Write the smallest version of the protection yourself, on a throwaway branch or in a `spike` package. Twenty lines is usually enough.
4. **Replace.** Delete your version, switch the framework's version back on, and compare behavior. Note anything the framework does that you did not.
5. **Pin.** Write a test that fails if the protection is removed, so the lesson stays in the codebase.

Then **read the source**. Each section lists the classes where the work happens. In IntelliJ, let it download sources, open the class (Ctrl+N), set a breakpoint, and step through one real request.

## Toolkit

| Tool | Use it for |
|---|---|
| Browser DevTools, **Network** tab | Request and response headers, `Set-Cookie`, CSRF headers, redirects |
| DevTools, **Application → Cookies** | Cookie flags (`HttpOnly`, `Secure`, `SameSite`), watching values change on login |
| DevTools, Network → a `ws` request → **Messages** | Every WebSocket frame sent and received (the STOMP frames are readable text) |
| `curl -i` / `curl -v` | Repeatable requests with full headers, and requests the browser would never send |
| [Burp Suite Community](https://portswigger.net/burp/communitydownload) or [OWASP ZAP](https://www.zaproxy.org/) | Intercepting and editing requests in flight, for example removing a CSRF header or changing a game ID |
| IntelliJ debugger | Breakpoints in the framework classes listed below |
| Logging | Add this to `application.properties` while studying (and remove it afterwards, because it logs sensitive values): |

```properties
# Every filter each request passes through, and why it was allowed or denied
logging.level.org.springframework.security=TRACE
# WebSocket handshake, STOMP frames, and message routing
logging.level.org.springframework.web.socket=DEBUG
logging.level.org.springframework.messaging=DEBUG
# SQL statements and their bound parameter values, logged separately
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.orm.jdbc.bind=TRACE
```

---

## 1. The security filter chain

**What's hidden.** Adding `spring-boot-starter-security` puts a `FilterChainProxy` in front of every request. It runs roughly 15 filters in a fixed order: CSRF checking, loading the security context from the session, logout, authentication, exception translation, and authorization. Nothing in your controllers shows this.

**Make it visible.**
- With `logging.level.org.springframework.security=TRACE`, the startup log prints the filter list, and each request logs `Invoking CsrfFilter (3/15)` and so on.
- `@EnableWebSecurity(debug = true)` prints a block for every request listing the filters it went through (for local study only).
- Set a breakpoint in `FilterChainProxy.doFilterInternal` and step through one request.

**Exercise.** Before you add Spring Security, write a plain `jakarta.servlet.Filter` that rejects any `/api/**` request without a session attribute `userId`. Register it with a `FilterRegistrationBean`. That is the core idea of the whole chain. Then delete it and add Spring Security.

**Gotcha.** If there is no `formLogin()` or `httpBasic()`, an unauthenticated request gets **403, not 401**, because the default entry point is `Http403ForbiddenEntryPoint`. A single-page app needs 401 to know it should redirect to login, so configure `exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))`. Write a test that asserts 401.

**Classes:** `FilterChainProxy`, `SecurityFilterChain`, `ExceptionTranslationFilter`, `AuthorizationFilter`.

---

## 2. Authentication flow

**What's hidden.** A login call goes `AuthenticationManager` → `ProviderManager` → `DaoAuthenticationProvider` → your `UserDetailsService` → `PasswordEncoder.matches`. On success, the resulting `Authentication` is put in a `SecurityContext`, and that context is stored in the HTTP session under the key `SPRING_SECURITY_CONTEXT`. On every later request, `SecurityContextHolderFilter` loads it back.

**Things you would not know unless you looked:**
- **Explicit save.** Since Spring Security 6, if you authenticate in your own controller (a JSON `POST /api/auth/login`), the context is **not** saved for you. Call `securityContextRepository.saveContext(context, request, response)` with an `HttpSessionSecurityContextRepository`. Without it, login returns 200 and the next request is anonymous.
- **Timing-attack protection.** When the username does not exist, `DaoAuthenticationProvider` still hashes a dummy password, so that "no such user" and "wrong password" take about the same time. Without that, an attacker could find valid usernames by timing the responses, even when your error messages are identical.

**Make it visible.**
- Set a breakpoint in `DaoAuthenticationProvider.additionalAuthenticationChecks` and in `retrieveUser`, then log in with a wrong password and with an unknown user.
- Time it: `curl -w "%{time_total}\n"` against the login endpoint 20 times with an unknown user and 20 times with a real user and a wrong password. Compare the averages.

**Exercise.** Write a login endpoint by hand first: look up the user, call `passwordEncoder.matches`, and put the user ID in `HttpSession`. Time unknown versus known users and watch the difference (BCrypt is slow, so it is large). Then add the dummy-hash fix yourself and time it again. Finally, replace it with `AuthenticationManager` and confirm Spring does the same thing.

**Classes:** `ProviderManager`, `DaoAuthenticationProvider`, `HttpSessionSecurityContextRepository`, `SecurityContextHolderFilter`.

---

## 3. Password storage

**What's hidden.** `PasswordEncoderFactories.createDelegatingPasswordEncoder()` stores hashes like `{bcrypt}$2a$10$N9qo8uLOickgx2ZMRZoMye...`. That one string contains:
- the algorithm ID (`{bcrypt}`), so you can migrate algorithms later
- the BCrypt version (`2a`)
- the cost factor (`10` means 2^10 rounds)
- a random 22-character salt
- the hash itself

You never handle the salt, which is why it is easy not to understand it.

**Make it visible.** Register two users with the same password and compare their stored hashes in the H2 console. They differ, because each has its own salt.

**Exercise.**
- Time `encode()` at cost 10, 12, and 14. Pick the cost that takes about 100–250 ms on your machine, and write down why a slow hash is the point.
- Split a stored hash into its parts by hand.
- Find out what happens with a password longer than 72 bytes (BCrypt ignores anything after byte 72; newer Spring Security versions reject it). Decide on a maximum password length based on that.
- Compare with `Argon2PasswordEncoder`: memory-hard rather than just slow.

**Classes:** `DelegatingPasswordEncoder`, `BCryptPasswordEncoder`, `BCrypt`.

---

## 4. Sessions and cookies

**What's hidden.** Tomcat creates the `JSESSIONID` cookie and Spring Boot sets `HttpOnly` on it by default. Spring Security also handles **session fixation** for you: on login, `ChangeSessionIdAuthenticationStrategy` gives the session a new ID. That way an attacker who planted a known session ID before login cannot use it afterwards.

**Make it visible.**
- In DevTools → Application → Cookies, write down the `JSESSIONID` value, log in, and watch the value change.
- `curl -i` the login endpoint and read the `Set-Cookie` header flags.
- Sessions are stored in memory in the backend process. Restart the backend and see that everyone is logged out.

**Exercise: session fixation.**
1. Set `sessionManagement(s -> s.sessionFixation(f -> f.none()))`.
2. In one browser profile, load the app and copy the pre-login `JSESSIONID`.
3. In a second profile, set that same cookie by hand (DevTools lets you edit cookies), then log in as the victim.
4. Back in the first profile, refresh: you are now logged in as the victim.
5. Turn protection back on and repeat. Write a test that asserts the session ID changes on login.

**Exercise: cookie flags.** Set `server.servlet.session.cookie.same-site` to `strict`, `lax`, and `none` in turn, and see where the app breaks or doesn't. Browsers treat a cookie with no `SameSite` attribute differently (Chrome defaults to `Lax`, others vary), which is why it is worth setting explicitly.

**Classes:** `ChangeSessionIdAuthenticationStrategy`, `SessionManagementFilter`, Tomcat's `Request.changeSessionId`.

---

## 5. CSRF tokens

**What's hidden.** `CsrfFilter` checks a token on every request except `GET`, `HEAD`, `TRACE`, and `OPTIONS`. In a single-page app setup:
- The token is stored in a readable `XSRF-TOKEN` cookie (`CookieCsrfTokenRepository.withHttpOnlyFalse()`).
- The client must echo it back in an `X-XSRF-TOKEN` header.
- An attacker's page can make the browser *send* your cookie, but it cannot *read* it, so it cannot produce the header. That is the whole defense (the "double-submit" pattern).

**Things you would not know unless you looked:**
- **Deferred tokens.** Since Spring Security 6, the token is not generated until something reads it. So the `XSRF-TOKEN` cookie can be missing on first load, and your first `POST` fails with 403. A common fix is a `GET /api/csrf` endpoint (or a filter) that reads the token, which the frontend calls on startup.
- **Masking (BREACH protection).** By default, `XorCsrfTokenRequestAttributeHandler` sends a *differently masked* token value each time, to defend against the BREACH compression attack. That is why the header value and the cookie value do not always look the same, and why `csrf.spa()` (or a hand-written request handler) exists to make cookie-based single-page apps work with it.
- Logout must be a `POST` once CSRF is on, so a link to `/logout` alone cannot log anyone out.

**Make it visible.** In DevTools, find the `XSRF-TOKEN` cookie and the `X-XSRF-TOKEN` request header on a `POST`. Then replay that `POST` with `curl`, once with the header and once without it.

**Exercise: build double-submit yourself.** With Spring's CSRF disabled, write a filter that:
1. On any request without an `XSRF-TOKEN` cookie, generates 32 random bytes (`SecureRandom`), base64url-encodes them, and sets the cookie (not `HttpOnly`, `SameSite=Lax`).
2. On `POST`/`PUT`/`PATCH`/`DELETE`, compares the cookie with the `X-XSRF-TOKEN` header using `MessageDigest.isEqual` (a constant-time comparison: find out why `String.equals` is the wrong choice).
3. Returns 403 when they don't match.

Run the evil-page attack from README Phase 3 against it. Then delete it, enable `csrf.spa()`, and list what Spring does that your version didn't.

**Classes:** `CsrfFilter`, `CookieCsrfTokenRepository`, `XorCsrfTokenRequestAttributeHandler`, `CsrfTokenRequestAttributeHandler`.

---

## 6. Default security headers

**What's hidden.** Spring Security adds headers to every response:
- `Cache-Control: no-cache, no-store, max-age=0, must-revalidate`
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `X-XSS-Protection: 0` (this deliberately *disables* an old browser filter that caused its own vulnerabilities)
- `Strict-Transport-Security`, but only on HTTPS requests

It does **not** add a `Content-Security-Policy`; you have to write that one.

**Make it visible.** Run `curl -i http://localhost:8080/api/ideas` before adding Spring Security, then again after, and compare the headers.

**Exercise.** For each header, write one sentence on the attack it prevents. Then turn each one off in turn and try to show the attack:
- Without `X-Frame-Options`, load the app in an `<iframe>` from the evil page (clickjacking).
- Without `nosniff`, see whether a browser will run a JSON response as script.

**Classes:** `HeaderWriterFilter`, `HeadersConfigurer`.

---

## 7. WebSocket handshake and STOMP

**What's hidden.** `spring-boot-starter-websocket` and `@EnableWebSocketMessageBroker` turn a plain HTTP `GET` with `Upgrade: websocket` into a persistent connection, and then speak STOMP over it. STOMP is a text protocol with frames such as `CONNECT`, `SUBSCRIBE`, `SEND`, and `MESSAGE`. Spring parses those frames, routes `/app/**` to your `@MessageMapping` methods, and fans `/topic/**` out to subscribers. It also rewrites `/user/queue/errors` into a per-session destination so only that user receives it.

**Make it visible.**
- In DevTools → Network, select the `ws` request. The **Headers** tab shows the `101 Switching Protocols` response and the `Sec-WebSocket-Key` / `Sec-WebSocket-Accept` pair. The **Messages** tab shows every STOMP frame as text, including heartbeats.
- Turn on `org.springframework.messaging=DEBUG` and follow one move from `SEND` to `MESSAGE`.

**Exercise: go below STOMP first.** Before Phase 5, build a raw `TextWebSocketHandler` that echoes messages, and connect to it from the browser console with `new WebSocket('ws://localhost:5173/ws-raw')`. Then:
- Implement "rooms" yourself with a `Map<String, Set<WebSocketSession>>`. Notice what you have to handle: concurrent sends, cleanup on disconnect, and making it per user.
- Switch to STOMP and map each piece of your code to the Spring component that replaced it.
- For extra depth, type one STOMP frame by hand: send `CONNECT\naccept-version:1.2\nhost:localhost\n\n\0` over a raw socket.

**Classes:** `WebSocketHttpRequestHandler`, `StompSubProtocolHandler`, `SimpleBrokerMessageHandler`, `UserDestinationMessageHandler`, `SimpAnnotationMethodMessageHandler`.

---

## 8. WebSocket security

**What's hidden.**
- **Who the user is.** The handshake is an ordinary HTTP request, so it carries the session cookie. Spring copies the authenticated `Principal` from it into the WebSocket session, which is why `@MessageMapping` methods can take a `Principal` parameter.
- **Origin check.** By default Spring only accepts same-origin handshakes (`OriginHandshakeInterceptor`). That is the CSWSH protection, and it is easy to undo by accident with `setAllowedOriginPatterns("*")` when CORS errors get annoying.
- **Per-message checks and CSRF.** `@EnableWebSocketSecurity` adds channel interceptors that check every message against your `AuthorizationManager<Message<?>>` rules, and that require a CSRF token in the STOMP `CONNECT` frame headers (`XorCsrfChannelInterceptor`).

**Things you would not know unless you looked:**
- **An open socket can outlive the login.** Once connected, the socket keeps its `Principal` even if the HTTP session times out or the user logs out in another tab.
- **Proxy quirks.** Through the Vite proxy, whether `Origin` and `Host` appear to match depends on proxy settings (`changeOrigin`). Check the actual headers the backend receives before trusting a "works in dev" result.

**Make it visible.** In DevTools, look at the `Origin` header on the handshake and at the headers on the `CONNECT` frame. With TRACE logging, find the line where a message is authorized or denied.

**Exercises.**
- **Session outliving logout:** open a game, then log out in another tab, then make a move from the first tab. Does the server accept it? Decide what *should* happen. Options include disconnecting sockets on logout (track sessions and close them from a `LogoutHandler`) or checking the session on each message.
- **Authorization at connect only:** write authorization *only* at `CONNECT` time, then subscribe to another game's `/topic/games/{id}` and watch its moves. That is why `SUBSCRIBE` needs its own membership check.
- **Missing CSRF token:** remove the CSRF header from the client's `CONNECT` frame and read the error the server returns.

**Classes:** `OriginHandshakeInterceptor`, `HttpSessionHandshakeInterceptor`, `AuthorizationChannelInterceptor`, `XorCsrfChannelInterceptor`, `MessageMatcherDelegatingAuthorizationManager`.

---

## 9. XSS protection in React

**What's hidden.** React writes `{value}` in JSX as a text node and escapes attribute values, so `<img onerror=...>` inside a chat message shows up as harmless text. You get output encoding without ever calling an escape function, which also means you may never learn where it stops protecting you.

**Where React does not protect you:**
- `dangerouslySetInnerHTML` (the name is the warning)
- URLs in `href` and `src`: a `javascript:` URL is a script, not text. Recent React versions block or warn about these, but test what *your* version does rather than relying on it.
- Anything outside React: `element.innerHTML`, `document.write`, third-party widgets, and Markdown renderers that output raw HTML
- Server-rendered or server-templated HTML. JSON encoding is not HTML encoding: a username stored as `<script>` is a perfectly valid JSON string.

**Make it visible.**
- In the browser console, run `document.querySelector('.chat').innerHTML` after receiving a malicious message, and see the escaped `&lt;img ...&gt;`.
- Look at the dev page source: Vite's dev server injects an **inline script** for React Refresh (it is in `index.html` as served on port 5173). A strict `Content-Security-Policy` will break the dev server, so test CSP against `npm run build` output served by the backend, or allow it only in dev.

**Exercise.**
- Write a 20-line plain-HTML page with no React: one div filled with `innerHTML`, one with `textContent`. Feed both the [OWASP XSS filter evasion](https://cheatsheetseries.owasp.org/cheatsheets/XSS_Filter_Evasion_Cheat_Sheet.html) payloads and see which ones fire.
- Write your own `escapeHtml` for the five characters `& < > " '`, use it with `innerHTML`, and confirm the payloads stop firing. Then find a payload that still works in an *attribute* that isn't quoted, or in an `href`. Encoding depends on context, and React handles those contexts for you.
- Add a CSP with `script-src 'self'` to the production build, put `dangerouslySetInnerHTML` back on a throwaway branch, and see the browser block the inline `onerror` handler even though the HTML was injected. That is CSP working as defense in depth.

---

## 10. SQL injection protection in JPA and Hibernate

**What's hidden.** Spring Data derived queries (`findByUsername`), JPQL with `:named` parameters, and `@Query` with parameters all turn into JDBC `PreparedStatement`s with `?` placeholders. The SQL text and the values travel to the database separately, so the value can never be parsed as SQL. You never see a `PreparedStatement`, so it is easy to think JPA "sanitizes" input. It doesn't. It never mixes input into the SQL text in the first place, and only for the APIs that bind parameters.

**Make it visible.** With `org.hibernate.SQL=DEBUG` and `org.hibernate.orm.jdbc.bind=TRACE`, every query logs as SQL text with `?` placeholders, followed by separate lines for each bound value. Send `' OR '1'='1` as a search term and watch it show up as a bound value, not as SQL.

**Where JPA does not protect you:**
- **JPQL built by string concatenation.** `em.createQuery("from User where name = '" + name + "'")` is JPQL injection. It is still injection, even without native SQL.
- Native queries built by concatenation, and `JdbcTemplate` with concatenated strings
- **`ORDER BY` and column or table names**, which cannot be bound parameters at all. Check how Spring Data handles a user-supplied `Sort` property (and what `JpaSort.unsafe` means) by sending a sort value like `username; drop table users` and reading the error.
- **`LIKE` wildcards.** A bound parameter still lets a user send `%` or `_`. That is not SQL injection, but it can make a search return every row or run slowly. Escape them if search results matter.

**Exercise.**
- Write the user search three ways in plain JDBC with no JPA: `Statement` with concatenation (exploit it, including a `UNION SELECT` that returns password hashes), `PreparedStatement` with `?` (exploit fails), and `PreparedStatement` built with concatenation anyway (exploit works again, which shows the class name alone does not protect you).
- Put a breakpoint in the JDBC driver's `PreparedStatement.setString` implementation (H2's `JdbcPreparedStatement`, or Postgres's `PgPreparedStatement`) and see the value arrive as data.
- Write the sort allowlist (`Map<String, String>` from API sort key to column name) and a test that sends an unknown key and expects 400.

---

## Quick reference

| Topic | README phase | Where to set breakpoints | The one test that proves it |
|---|---|---|---|
| Filter chain | 2 | `FilterChainProxy` | Unauthenticated `/api/**` returns 401 |
| Login flow | 2 | `DaoAuthenticationProvider` | After login, `/api/auth/me` returns the user |
| Password storage | 2 | `BCryptPasswordEncoder` | Stored hash is not the password; same password gives different hashes |
| Session fixation | 2 | `ChangeSessionIdAuthenticationStrategy` | Session ID differs before and after login |
| CSRF | 3 | `CsrfFilter` | `POST` without token returns 403; with `.with(csrf())` returns 2xx |
| Security headers | 3, 7 | `HeaderWriterFilter` | Response includes `nosniff`, `X-Frame-Options`, and your CSP |
| IDOR | 4 | your service | Non-member gets 403 on another game |
| WebSocket routing | 5 | `SimpAnnotationMethodMessageHandler` | Two STOMP clients play a full game |
| WebSocket security | 5, 6 | `AuthorizationChannelInterceptor`, `OriginHandshakeInterceptor` | Foreign `Origin` rejected; non-member `SUBSCRIBE` denied |
| XSS | 7 | n/a (browser) | Malicious chat message renders as text, no element injected |
| SQL injection | 8 | `JdbcPreparedStatement.setString` | Injection payload is matched as a literal string |

## Further reading

- [PortSwigger Web Security Academy](https://portswigger.net/web-security): free hands-on labs for CSRF, XSS, SQL injection, WebSockets, and authentication. Doing the matching labs before each phase is the best way to learn the attacker's side.
- OWASP Cheat Sheets: [CSRF Prevention](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html), [XSS Prevention](https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html), [SQL Injection Prevention](https://cheatsheetseries.owasp.org/cheatsheets/SQL_Injection_Prevention_Cheat_Sheet.html), [Session Management](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html), [Password Storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html), [WebSocket Security](https://cheatsheetseries.owasp.org/cheatsheets/WebSocket_Security_Cheat_Sheet.html)
- [Spring Security reference](https://docs.spring.io/spring-security/reference/): read the *Architecture* page first, then *CSRF*, *Session Management*, and *WebSocket Security*
- [RFC 6455](https://www.rfc-editor.org/rfc/rfc6455) (the WebSocket protocol, sections 1 and 4 cover the handshake) and the [STOMP 1.2 spec](https://stomp.github.io/stomp-specification-1.2.html) (short and readable)
