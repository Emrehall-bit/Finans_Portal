# Finance Portal Frontend MVP

## Run the frontend

1. `cd frontend`
2. `npm install`
3. `npm run dev`

Frontend runs on default Vite URL (usually `http://localhost:5173`).

## Change backend base URL

Update `src/api/config.js`:

- `API_CONFIG.BASE_URL` (default: `http://localhost:8080`)

## Change demo user id

Update `src/api/config.js`:

- `API_CONFIG.DEMO_USER_ID` (default: `1`)

This id is used in `Portfolio`, `Watchlist`, and `Alerts` pages because auth is not required yet.

## Read-only notes

Current pages are mostly writable because backend endpoints exist for create/list/remove/cancel operations.

- **No hard delete/update for alerts items** in UI beyond the available cancel endpoint.
- **No portfolio item edit/remove** because backend controller does not expose these endpoints currently.
