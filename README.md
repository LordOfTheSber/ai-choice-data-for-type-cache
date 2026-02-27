# K8s/OpenShift Log Downloader Monorepo

## Structure
- `backend` — Spring Boot 3 / Java 21 / Fabric8 Kubernetes Client
- `frontend` — Vite + React + TypeScript

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

## API examples
```bash
curl 'http://localhost:8080/api/v1/contours'
curl 'http://localhost:8080/api/v1/namespaces?contour=contour-a'
curl -X POST 'http://localhost:8080/api/v1/logs/preview' \
  -H 'Content-Type: application/json' \
  -d '{"contour":"contour-a","namespace":"default","selector":"app=my-app","from":"2025-01-01T00:00:00Z","to":"2025-01-01T01:00:00Z","bestEffort":true}'

curl -X POST 'http://localhost:8080/api/v1/logs/download' \
  -H 'Content-Type: application/json' \
  -o logs.zip \
  -d '{"contour":"contour-a","namespace":"default","selector":"app=my-app","bestEffort":true}'
```

## Kubernetes deploy
- Build image from `backend/Dockerfile`.
- Apply `backend/k8s/rbac.yaml` then `backend/k8s/deployment.yaml`.

## Assumptions
- Log lines may start with RFC3339/ISO timestamp; non-parsable lines are retained and counted.
- `to` filtering is done server-side after stream starts from `from`/`sinceTime`.
- Any pod/container failure goes into `errors/<pod>.txt`; with `bestEffort=false` request fails fast.
