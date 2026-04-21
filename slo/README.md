# SLO Testing for YDB Spring Retry

**SLO (Service Level Objectives)** tests verify SDK reliability under adverse conditions: node failures, tablet restarts, and network partitions — the kind of events that happen routinely in a large distributed database cluster.

## Structure

```
slo/
├── playground/   Local Docker Compose stack (YDB cluster + Prometheus + Grafana + chaos)
│   ├── chaos/             Standard chaos configuration
│   └── chaos-aggressive/  Aggressive chaos configuration
└── src/          SLO workload tool (Java / Spring Boot)
```

## Quick Start (local)

### Prerequisites

- Docker with Docker Compose v2
- Java 21
- Maven 3.9+

### 1. Build the ydb-spring-retry library

```bash
mvn install -DskipTests
```

### 2. Start the playground

There are two chaos configurations:

| Configuration | Directory | Description |
|---|---|---|
| **Standard** | `playground/chaos/` | Stop/start + restart + SIGKILL on random nodes |
| **Aggressive** | `playground/chaos-aggressive/` | 6 phases: pause, multi-node kill, instant restart, dual pause, rapid kill/start, triple SIGKILL. Also limits resources per node (1 CPU, 768M RAM). |

```bash
# Standard chaos
cd slo/playground/chaos
docker compose up -d
```

or

```bash
# Aggressive chaos
cd slo/playground/chaos-aggressive
docker compose up -d
```

Services (same for both):

| Service | URL |
|---|---|
| Grafana | http://localhost:3000 (admin/admin) |
| Prometheus | http://localhost:9090 |
| YDB monitoring | http://localhost:8765 |
| YDB gRPC | grpc://localhost:2136 |

### 3. Run the workload

```bash
cd slo
mvn package -DskipTests

JAR=target/ydb-slo-workload-1.0.0-SNAPSHOT-exec.jar

# Create the test table
java -jar $JAR create grpc://localhost:2136 /Root/testdb

# Run read/write workload for 10 minutes
java -jar $JAR run grpc://localhost:2136 /Root/testdb \
  --otlp-endpoint http://localhost:9090/api/v1/otlp/v1/metrics \
  --read-rps 1000 --write-rps 100 --time 600

# Drop the table when done
java -jar $JAR cleanup grpc://localhost:2136 /Root/testdb
```

### 4. View metrics in Grafana

Open http://localhost:3000 — the SLO dashboard is auto-provisioned.

## Detailed Documentation

- [Workload CLI reference](src/README.md) — all commands and arguments
- [Playground setup](playground/README.md) — Docker Compose services, chaos configurations, and config
