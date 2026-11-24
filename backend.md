# Backend Guide: Spring Boot API

A JSON-only REST API built on Spring Boot 3.3 and Java 17, with JPA persistence to an H2 file database. This guide covers the layout, the entry points, and how each piece is wired to its framework.

## 1. General structure

```
interview-starter/
├── pom.xml                              Maven build and dependencies
├── data/                                H2 database files (created at first run)
└── src/main/
    ├── java/com/example/interview/
    │   ├── InterviewApplication.java          Entry point
    │   ├── model/Idea.java                    JPA entity and validation rules
    │   ├── repository/IdeaRepository.java     Data access interface
    │   ├── service/IdeaService.java           Business logic
    │   └── controller/IdeaApiController.java  REST endpoints
    └── resources/
        └── application.properties       Datasource, JPA, error settings
```

The layering is **Controller → Service → Repository → Database**. Controllers stay about HTTP, the service holds business logic (such as voting), and repositories stay about persistence.

## 2. Entry points

### Application entry: `InterviewApplication`

`main()` calls `SpringApplication.run(...)`. The `@SpringBootApplication` annotation on the class combines three things:

- **`@ComponentScan`** scans `com.example.interview` and all sub-packages, so the controller and repository are picked up automatically. Anything placed outside this package tree will not be found.
- **`@EnableAutoConfiguration`** inspects the classpath and configures beans for you. Because `spring-boot-starter-web` is present you get an embedded Tomcat and `DispatcherServlet`. Because `spring-boot-starter-data-jpa` and `h2` are present you get a `DataSource`, Hibernate, and a transaction manager.
- **`@Configuration`** lets the class itself define beans if you need to.

### HTTP entry: `IdeaApiController`

| Method | Path | Behavior |
| --- | --- | --- |
| GET | `/api/ideas` | Returns all ideas as JSON |
| POST | `/api/ideas` | Validates the body, saves, returns the saved idea with status 201 |
| POST | `/api/ideas/{id}/vote` | Adds one vote and returns the updated idea |

### Operational entry: H2 console

`http://localhost:8080/h2-console` with JDBC URL `jdbc:h2:file:./data/interview`, user `sa`, blank password. Useful for confirming rows during a live session.

## 3. How the pieces connect to the frameworks

### Request flow

```
HTTP request
  → embedded Tomcat
  → DispatcherServlet (Spring MVC)
  → IdeaApiController (matched by @RequestMapping)
  → Jackson deserializes JSON body into Idea
  → Bean Validation runs because of @Valid
  → IdeaService
  → IdeaRepository.save()   (Spring Data JPA proxy)
  → Hibernate generates SQL
  → H2 via JDBC
  → Jackson serializes the result back to JSON
```

### Spring MVC (`spring-boot-starter-web`)

- `@RestController` marks the class as a controller whose return values are written straight to the response body, not resolved as view names.
- `@RequestMapping("/api/ideas")` sets the base path, with `@GetMapping` and `@PostMapping` for the methods.
- `@RequestBody` tells MVC to deserialize the JSON body into an `Idea` using Jackson.
- `@ResponseStatus(HttpStatus.CREATED)` sets the 201 on create.

### Bean Validation (`spring-boot-starter-validation`)

- Constraints live on the entity: `@NotBlank` and `@Size(max = 200)` on `title`, `@Size(max = 2000)` on `description`.
- `@Valid` on the controller parameter triggers them. A failure throws `MethodArgumentNotValidException`, which `GlobalExceptionHandler` turns into a 400 with an `errors` array of `{ field, message }`. The frontend shows the first one.

### Error responses (`exception/`)

`GlobalExceptionHandler` is a `@RestControllerAdvice` that returns every error as an RFC 9457 problem (`application/problem+json`, with `status`, `title`, `detail`):

| Cause | Status |
| --- | --- |
| `IdeaNotFoundException` (for example, voting on an unknown id) | 404 |
| Validation failure (adds an `errors` array) | 400 |
| Malformed JSON, non-numeric id, wrong HTTP method, unknown route | 400 / 405 / 404 (handled by the `ResponseEntityExceptionHandler` base class) |
| Anything else | 500 with a generic message. The real exception is logged, never sent to the client. |

To map a new exception, add an `@ExceptionHandler` method that returns a `ProblemDetail`.

### Spring Data JPA and Hibernate (`spring-boot-starter-data-jpa`)

- `IdeaRepository extends JpaRepository<Idea, Long>` is only an interface. Spring generates the implementation at startup, giving you `save`, `findAll`, `findById`, `deleteById`, and so on.
- Custom queries can be added as derived methods, for example `List<Idea> findByTitleContainingIgnoreCase(String q)`, with no SQL needed.
- `Idea` is mapped with `@Entity`. The id uses `@GeneratedValue(strategy = IDENTITY)`, so the database assigns it. `createdAt` is initialized when the object is constructed.
- `spring.jpa.hibernate.ddl-auto=update` makes Hibernate create or alter tables from the entity on startup. That is convenient for an interview and not appropriate for production, where you would use migrations (Flyway or Liquibase).
- `spring.jpa.open-in-view=false` keeps the Hibernate session from staying open during view rendering. It is the safer default for an API.

### Database (H2)

- `spring.datasource.url=jdbc:h2:file:./data/interview;AUTO_SERVER=TRUE` stores data in a file relative to the working directory, so it survives restarts. `AUTO_SERVER` lets the H2 console and the app connect at the same time.
- To use Postgres, swap the `h2` dependency in `pom.xml` for `org.postgresql:postgresql` and update the three `spring.datasource.*` properties. No code changes are needed.

### Build and dev tooling

- `spring-boot-starter-parent` supplies dependency versions, so most dependencies in `pom.xml` have no version tag.
- `spring-boot-devtools` restarts the app on classpath changes when you run from an IDE.
- `mvn spring-boot:run` or the green arrow on `InterviewApplication` in IntelliJ both start the server on port 8080.

## 4. Common changes during the interview

| Task | Where to change |
| --- | --- |
| Add or rename a field | `Idea.java` (Hibernate updates the table), then `frontend/src/types/ideaTypes.ts` |
| New resource | New `@Entity`, a repository interface, and a controller in the same package tree |
| Another endpoint | Add a method to the controller, for example `@GetMapping("/{id}")` or `@DeleteMapping("/{id}")` |
| Business logic | Add a method to `IdeaService` and call it from the controller |
| New error case | Throw a specific exception and map it in `GlobalExceptionHandler` |
| Different database | `pom.xml` dependency plus `application.properties` |

## 5. Talking points and tradeoffs

- **Entity as API model.** `Idea` is used directly for requests and responses. This is fast to build, but it couples the API to the schema and lets clients send fields like `id` or `createdAt`. The usual next step is request and response DTOs (Java records work well).
- **No pagination.** `findAll()` returns everything. Switch to `Pageable` if the list could grow.
- **Constructor injection.** The controller takes its service, and the service its repository, through the constructor, which keeps it easy to test with a mock.
- **Testing options.** `@DataJpaTest` for repository behavior, `@WebMvcTest` with `MockMvc` for controller and validation behavior.
- **Security and CORS.** Neither is configured. In dev the Vite proxy avoids CORS. For a deployed split frontend you would add CORS configuration or serve the built frontend from Spring's `static` folder.
