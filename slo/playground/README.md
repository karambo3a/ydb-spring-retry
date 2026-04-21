# SLO Playground

Local Docker Compose stack for running SLO workloads against a real YDB cluster with full metrics visibility in Grafana and automated chaos injection.

## Services

| Service | URL | Description |
|---|---|---|
| Grafana | http://localhost:3000 (admin/admin) | Metrics dashboards |
| Prometheus | http://localhost:9090 | Metrics storage (OTLP receiver enabled) |
| YDB monitoring | http://localhost:8765 | YDB cluster UI (static node) |
| YDB gRPC | grpc://localhost:2136 | YDB endpoint (database node 1) |
| Chaos | — | Automated fault injection container |

The cluster consists of 1 static node + 5 database (dynamic) nodes running YDB 24.4.4.12.

## Usage

### Standard chaos

```bash
cd slo/playground/chaos
docker compose up -d
```

### Aggressive chaos

```bash
cd slo/playground/chaos-aggressive
docker compose up -d
```

### Stop

```bash
docker compose down
```

## Chaos Configurations

### Standard (`chaos/`)

Routine node failure simulation:

1. **5 iterations** of `docker stop` / `docker start` on a random node (10s graceful stop, 60s intervals)
2. **3 iterations** of `docker restart` on a random node (instant, no grace period)
3. **1 final** `SIGKILL` on a random node (node stays dead)

### Aggressive (`chaos-aggressive/`)

Intensive fault injection across 6 phases, each targeting specific failure modes:

| Phase | Action | Expected errors |
|---|---|---|
| 1. Pause/unpause (4x) | `docker pause` for 20s | `TIMEOUT` — connections hang, operations expire |
| 2. Multi-node kill (3x) | SIGKILL 2 nodes simultaneously | `OVERLOADED`, `BAD_SESSION` on survivors |
| 3. Instant restart (3x) | `docker restart -t 0` | `TRANSPORT_UNAVAILABLE` |
| 4. Dual pause (1x) | Pause 2 nodes for 30s | Extended `TIMEOUT`, `OVERLOADED` |
| 5. Rapid kill/start (5x) | SIGKILL + immediate start | `SESSION_BUSY`, `BAD_SESSION` (pool thrashing) |
| 6. Triple SIGKILL (1x) | Kill 3 nodes simultaneously | Cluster partially down |

Resource limits per node: **1 CPU**, **768M RAM** (aggressive config only).

## Configuration

| Path | Description |
|---|---|
| `configs/ydb.yaml` | YDB cluster configuration |
| `configs/prometheus/prometheus.yaml` | Prometheus scrape config |
| `configs/grafana/provisioning/` | Auto-provisioned datasources and dashboards |

YDB runs in **non-persistent** mode — data is lost on container restart.

## Next Steps

After the playground is running, use the [SLO workload tool](../src/README.md) to create a test table and drive load against YDB.
