# Frontend Guide: React + TypeScript + Vite

A single-page app built with React 18, TypeScript (strict mode), and Vite 5. It talks to the Spring Boot backend over JSON. This guide covers the layout, the entry points, and how each piece connects to its tooling.

## 1. General structure

```
interview-starter/
├── package.json                  Root runner: npm run setup / npm run dev
└── frontend/
    ├── package.json              Frontend dependencies and scripts
    ├── index.html                HTML shell, the real browser entry
    ├── vite.config.ts            Dev server, React plugin, /api proxy
    ├── tsconfig.json             TypeScript settings (strict)
    └── src/
        ├── main.tsx              JavaScript entry: mounts React
        ├── App.tsx               Page shell, renders IdeaView
        ├── api/
        │   ├── http.ts           Generic request<T>() fetch helper
        │   └── ideaApi.ts        All idea calls to the backend
        ├── component/
        │   ├── IdeaView.tsx      Form, state, and submit/vote handlers
        │   ├── IdeaList.tsx      Renders the list of ideas
        │   └── IdeaCard.tsx      Renders one idea with its vote button
        ├── types/ideaTypes.ts    TypeScript types mirroring the backend entity
        └── styles.css            Global styles
```

The split is intentional. **Components** own UI and state, **`api/`** owns network calls, and **`types/`** owns the data shapes. Components never call `fetch` directly, which keeps them easy to read and easy to change when the API changes.

## 2. Entry points

### Browser entry: `index.html`

Vite serves `index.html` as the page. It contains a `<div id="root">` and a `<script type="module" src="/src/main.tsx">` tag. Vite compiles the TypeScript on the fly in dev, so the browser receives plain modules.

### JavaScript entry: `src/main.tsx`

Imports `App` and the stylesheet, then calls React 18's `createRoot(document.getElementById('root')!).render(<App />)`. This is the only place React is attached to the page.

### Application entry: `src/App.tsx`

The root component. It renders the page heading and `IdeaView`, which fetches ideas on mount, holds form state, handles submit and voting, and renders the list.

### Developer entry: npm scripts

| Command | Where | What it does |
| --- | --- | --- |
| `npm run setup` | repo root | Installs root and frontend dependencies |
| `npm run dev` | repo root | Starts Spring Boot and Vite together via `concurrently` |
| `npm run dev` | `frontend/` | Starts only the Vite dev server on port 5173 |
| `npm run build` | `frontend/` | Type-checks with `tsc --noEmit`, then builds to `dist/` |
| `npm test` | `frontend/` | Runs the Vitest unit tests once (`npm run test:watch` for watch mode) |

### Tests

Tests use **Vitest** (configured in the `test` block of `vite.config.ts`) with **React Testing Library** in a jsdom environment. Test files sit next to the code they cover as `*.test.ts(x)`. `src/test/setup.ts` adds the jest-dom matchers and cleans up after each test, and `src/test/fixtures.ts` has `makeIdea()` and `jsonResponse()` helpers. API tests stub the global `fetch`. `IdeaView` tests mock the `ideaApi` module with `vi.mock`.

## 3. How the pieces connect to the frameworks

### React

- **State.** `IdeaView` uses `useState` for `ideas`, `title`, `description`, and `error`. Form inputs are controlled: each one's `value` comes from state and `onChange` updates it.
- **Loading data.** `useEffect(..., [])` runs once after the first render and calls `ideaApi.listIdeas()`.
- **Submitting.** `handleSubmit` calls `preventDefault()`, clears any old error, awaits `ideaApi.createIdea(...)`, then reloads the list and resets the form. The list comes from the server's response, so the UI shows what was actually stored.
- **Voting.** `handleVote` calls `ideaApi.upvote(id)` and reloads the list.
- **Rendering.** `IdeaList` maps over `ideas` and renders an `IdeaCard` for each, using `id` as the `key`.

### TypeScript

- `tsconfig.json` has `strict: true`, `jsx: react-jsx` (no need to import React in each file), and `noEmit` because Vite does the compiling and `tsc` is only used for checking.
- `types/ideaTypes.ts` defines `Idea` and `NewIdea`. These must be kept in sync by hand with `Idea.java`. If the backend adds a field, add it here first and the compiler will point to every place that needs to change.
- `api/http.ts` provides a generic helper, `request<T>()`, so each function in `ideaApi.ts` states its return type, for example `listIdeas = () => request<Idea[]>('/api/ideas')`.

### Vite

- **Dev server.** `vite.config.ts` registers `@vitejs/plugin-react` for JSX transforms and fast refresh, so edits appear in the browser without a full reload.
- **Build.** `vite build` bundles to `frontend/dist/`.

### Connection to the backend

The browser only ever talks to Vite on port 5173. Vite's `server.proxy` forwards anything starting with `/api` to `http://localhost:8080`:

```
Browser  →  http://localhost:5173/api/ideas
  Vite proxy  →  http://localhost:8080/api/ideas
  Spring Boot  →  JSON response  →  back to the browser
```

Because the browser sees one origin, **no CORS configuration is needed** on the backend. Code calls relative URLs such as `/api/ideas`, never a hard-coded host.

### Error handling path

- `request()` in `api/http.ts` checks `res.ok`. On failure it tries to parse the JSON error body and throws an `Error` using the first validation message (`errors[0].defaultMessage`), then `message`, then a generic status string.
- `IdeaView` catches failures from loading, submitting and voting, and stores the message in `error`, which renders in a red block (`role="alert"`) above the button. A blank title is rejected before any request is sent. If create fails, the form keeps its values so the user can retry. Each new action clears the previous error.
- This is why the backend setting `server.error.include-binding-errors=always` matters: it is what puts the validation text in the response.

### Root runner

The root `package.json` depends on `concurrently` and defines `dev` as two processes: `mvn spring-boot:run` (labelled `api`) and `npm --prefix frontend run dev` (labelled `web`).

## 4. Common changes during the interview

| Task | Where to change |
| --- | --- |
| New field in the form | `types/ideaTypes.ts`, `IdeaView.tsx` state and inputs, and the Java entity |
| New API call | Add a typed function in `api/ideaApi.ts`, call it from a component |
| Split the UI further | Extract an `IdeaForm.tsx` from `IdeaView.tsx`, passing props and callbacks |
| Client-side validation | Check values in `handleSubmit` before calling the API, and set `error` |
| Multiple pages | Add `react-router-dom` and move `App` content into route components |
| Different styling | Edit `styles.css`, or add a CSS library through npm |

## 5. Talking points and tradeoffs

- **State in one view.** All idea state lives in `IdeaView`. It can move into a custom hook such as `useIdeas()` once there is more than one screen.
- **Local state only.** `useState` is enough here. A library like React Query would add caching, retries, and refetching if the app grew.
- **Optimistic vs confirmed updates.** The list updates after the server responds. That is simple and always correct, at the cost of a short delay.
- **Hand-written types.** They can drift from the backend. Generating types from an OpenAPI spec solves this and is a good answer if asked about scaling.
- **Production serving.** Run `npm run build` in `frontend/` and copy `dist/` into the backend's `src/main/resources/static/` so Spring serves the app itself. Alternatively, deploy the frontend separately and add CORS to the backend.
- **Accessibility and UX gaps to mention.** There is no disabled state while submitting, no empty-list message, and no inline field-level errors. All are quick wins if the interviewer asks.
