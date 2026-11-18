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
        ├── App.tsx               The one component: form, list, state
        ├── api.ts                All fetch calls to the backend
        ├── types.ts              TypeScript types mirroring backend entities
        └── styles.css            Global styles
```

The split is intentional. **`App.tsx`** owns UI and state, **`api.ts`** owns network calls, and **`types.ts`** owns the data shapes. Components never call `fetch` directly, which keeps them easy to read and easy to change when the API changes.

## 2. Entry points

### Browser entry: `index.html`

Vite serves `index.html` as the page. It contains a `<div id="root">` and a `<script type="module" src="/src/main.tsx">` tag. Vite compiles the TypeScript on the fly in dev, so the browser receives plain modules.

### JavaScript entry: `src/main.tsx`

Imports `App` and the stylesheet, then calls React 18's `createRoot(document.getElementById('root')!).render(<App />)`. This is the only place React is attached to the page.

### Application entry: `src/App.tsx`

The root component. It fetches items on mount, holds form state, handles submit, and renders the list.

### Developer entry: npm scripts

| Command | Where | What it does |
| --- | --- | --- |
| `npm run setup` | repo root | Installs root and frontend dependencies |
| `npm run dev` | repo root | Starts Spring Boot and Vite together via `concurrently` |
| `npm run dev` | `frontend/` | Starts only the Vite dev server on port 5173 |
| `npm run build` | `frontend/` | Type-checks with `tsc --noEmit`, then builds to `dist/` |

## 3. How the pieces connect to the frameworks

### React

- **State.** `App` uses `useState` for `items`, `title`, `description`, `error`, and `loading`. Form inputs are controlled: each one's `value` comes from state and `onChange` updates it.
- **Loading data.** `useEffect(..., [])` runs once after the first render and calls `listItems()`. The `.finally()` clears the loading flag whether the call worked or failed.
- **Submitting.** `handleSubmit` calls `preventDefault()`, clears any old error, awaits `createItem(...)`, then prepends the saved item to `items` and resets the form. The list updates from the server's response, so the UI shows what was actually stored.
- **Rendering.** `items.map(...)` renders the list, using `id` as the `key`.

### TypeScript

- `tsconfig.json` has `strict: true`, `jsx: react-jsx` (no need to import React in each file), and `noEmit` because Vite does the compiling and `tsc` is only used for checking.
- `types.ts` defines `Item` and `NewItem`. These must be kept in sync by hand with `Item.java`. If the backend adds a field, add it here first and the compiler will point to every place that needs to change.
- `api.ts` uses a generic helper, `request<T>()`, so each exported function states its return type, for example `listItems = () => request<Item[]>('/api/items')`.

### Vite

- **Dev server.** `vite.config.ts` registers `@vitejs/plugin-react` for JSX transforms and fast refresh, so edits appear in the browser without a full reload.
- **Build.** `vite build` bundles to `frontend/dist/`.

### Connection to the backend

The browser only ever talks to Vite on port 5173. Vite's `server.proxy` forwards anything starting with `/api` to `http://localhost:8080`:

```
Browser  →  http://localhost:5173/api/items
  Vite proxy  →  http://localhost:8080/api/items
  Spring Boot  →  JSON response  →  back to the browser
```

Because the browser sees one origin, **no CORS configuration is needed** on the backend. Code calls relative URLs such as `/api/items`, never a hard-coded host.

### Error handling path

- `request()` in `api.ts` checks `res.ok`. On failure it tries to parse the JSON error body and throws an `Error` using the first validation message (`errors[0].defaultMessage`), then `message`, then a generic status string.
- `App` catches the error and stores its message in `error`, which renders in a red block above the button.
- This is why the backend setting `server.error.include-binding-errors=always` matters: it is what puts the validation text in the response.

### Root runner

The root `package.json` depends on `concurrently` and defines `dev` as two processes: `mvn spring-boot:run` (labelled `api`) and `npm --prefix frontend run dev` (labelled `web`).

## 4. Common changes during the interview

| Task | Where to change |
| --- | --- |
| New field in the form | `types.ts`, `App.tsx` state and inputs, and the Java entity |
| New API call | Add a typed function in `api.ts`, call it from a component |
| Split the UI | Extract `ItemForm.tsx` and `ItemList.tsx` from `App.tsx`, passing props and callbacks |
| Client-side validation | Check values in `handleSubmit` before calling the API, and set `error` |
| Multiple pages | Add `react-router-dom` and move `App` content into route components |
| Different styling | Edit `styles.css`, or add a CSS library through npm |

## 5. Talking points and tradeoffs

- **Single component.** Everything lives in `App.tsx` to start fast. State logic can move into a custom hook such as `useItems()` once there is more than one screen.
- **Local state only.** `useState` is enough here. A library like React Query would add caching, retries, and refetching if the app grew.
- **Optimistic vs confirmed updates.** The list updates after the server responds. That is simple and always correct, at the cost of a short delay.
- **Hand-written types.** They can drift from the backend. Generating types from an OpenAPI spec solves this and is a good answer if asked about scaling.
- **Production serving.** Run `npm run build` in `frontend/` and copy `dist/` into the backend's `src/main/resources/static/` so Spring serves the app itself. Alternatively, deploy the frontend separately and add CORS to the backend.
- **Accessibility and UX gaps to mention.** There is no disabled state while submitting, no empty-list message, and no inline field-level errors. All are quick wins if the interviewer asks.
