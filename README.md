# K8s/OpenShift Log Downloader Monorepo

## Structure
- `backend` — Spring Boot 3 / Java 21 / Fabric8 Kubernetes Client
- `frontend` — Vite + React + TypeScript (modernized UI/UX + Redux cache)

## Connection semantics (ported as-is)
- `K8sClientFactory.createClient(contour)` → `testsK8sUrl + testsK8sToken`
- `K8sClientFactory.createClientForMaster(contour)` → `testsK8sUrl + masterK8sToken`
- Main mode: contour-based config.
- Fallback: if contour is empty, Fabric8 default config (in-cluster SA or local kubeconfig).
- TLS skip verify enabled by default via `app.k8s.skipTlsVerify=true`.
- TLS workaround for some clusters: `-Djdk.tls.client.protocols=TLSv1.2`.

## Backend run
```bash
cd backend
mvn spring-boot:run
```

Configure contours in `backend/src/main/resources/application.yml` or env vars, e.g.:
```bash
export CONTOUR_A_TESTS_K8S_URL=https://api.cluster:6443
export CONTOUR_A_TESTS_K8S_TOKEN=...
export CONTOUR_A_MASTER_K8S_TOKEN=...
```

Swagger UI: `http://localhost:8080/swagger-ui.html`

## Frontend run
```bash
cd frontend
npm install
npm run dev
```

## What changed
- Async collect does not sleep in real wall-clock time for each point; it iterates period points immediately and queries K8s per period writing results to file.
- If period (`pollIntervalSeconds`) is `0` or negative, backend performs a single one-shot log snapshot for the selected interval (no periodic polling).
- ZIP generation fixed: metadata is written without closing target `ZipOutputStream`, preventing corrupted archives and `Stream closed` on finalize.
- Backend debugging logs: HTTP request/response bodies, status and exception traces are logged; service logs include async job lifecycle.
- Async collect job now tolerates late `Stream closed` after partial archive write by finalizing job as DONE with warning when ZIP exists.
- Пользовательский сценарий: contour-шаблон (например `sb_mg`) -> namespace -> pods/containers -> период времени (MSK) -> backend опрашивает логи через заданный период до конечного времени и один раз за его предел -> снапшоты склеиваются по совпадению последней строки; если совпадения нет, вставляется маркер `=====NO_OVERLAP_BOUNDARY=====`.
- UI state (namespace/selector/pods/containers/time range/options) is persisted to file-backed storage via backend endpoint `PUT/GET /api/v1/preferences` (`backend/data/ui-preferences.json` at runtime).
- Pods/containers listing is cached on frontend in Redux store for faster repeated selection flows.
- DateTimePicker uses Moscow time and backend parses Moscow-local values to match log lines like `2026-02-26 17:10:12,447`.
- Time-window log reading supports periodic chunking (`pollIntervalSeconds`) from `from` (x) to `to` (y) with stitching.
- Async collection flow for UI button "Сбор логов": `POST /api/v1/logs/collect` -> periodic status poll `GET /api/v1/logs/collect/{jobId}` -> final download `GET /api/v1/logs/collect/{jobId}/download`.
- Container discovery includes regular + init + ephemeral containers. Added `GET /api/v1/containers` for complete container list for selected pods/selector.

## API examples
```bash
curl 'http://localhost:8080/api/v1/contours'
curl 'http://localhost:8080/api/v1/namespaces?contour=contour-a'
curl 'http://localhost:8080/api/v1/containers?contour=contour-a&namespace=default&selector=app=my-app'
curl 'http://localhost:8080/api/v1/preferences'

curl -X PUT 'http://localhost:8080/api/v1/preferences' \
  -H 'Content-Type: application/json' \
  -d '{"namespace":"default","selector":"app=my-app"}'

curl -X POST 'http://localhost:8080/api/v1/logs/preview' \
  -H 'Content-Type: application/json' \
  -d '{"contour":"contour-a","namespace":"default","selector":"app=my-app","from":"2025-01-01T00:00:00Z","to":"2025-01-01T01:00:00Z","pollIntervalSeconds":30,"bestEffort":true}'

curl -X POST 'http://localhost:8080/api/v1/logs/download' \
  -H 'Content-Type: application/json' \
  -o logs.zip \
  -d '{"contour":"contour-a","namespace":"default","selector":"app=my-app","pollIntervalSeconds":30,"bestEffort":true}'
```

## Kubernetes deploy
- Build image from `backend/Dockerfile`.
- Apply `backend/k8s/rbac.yaml` then `backend/k8s/deployment.yaml`.

## Assumptions
- Log lines should start with RFC3339/ISO timestamp for strict window chunking.
- Non-parsable lines are retained for non-window mode and are counted in metadata.
- Any pod/container failure goes into `errors/<pod>.txt`; with `bestEffort=false` request fails fast.
