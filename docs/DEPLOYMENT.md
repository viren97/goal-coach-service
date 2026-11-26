# Deployment, configuration and secrets

## Images

The `Dockerfile` is a two-stage build (JDK build → JRE runtime). It caches the dependency layer, runs as
non-root user `1001`, and sizes the heap from the container limit (`-XX:MaxRAMPercentage=75`).

```bash
docker build -t ghcr.io/viren97/goal-coach-service:0.0.1 .
```

## Local stack

`docker-compose.yml` runs Postgres 16, Redis 7 (no persistence, cache only), Kafka 3.8 (KRaft, no ZooKeeper)
and the app with the `docker` profile. The credentials in it are local-only defaults.

## Kubernetes (Helm)

```bash
helm upgrade --install coach deploy/helm/coach \
  --set image.tag=0.0.1 \
  --set config.FCM_PROJECT_ID=my-firebase-project
```

The chart creates:

| Object | Purpose |
|---|---|
| `ConfigMap` | Non-secret env (`values.yaml → config`); pods roll when it changes (checksum annotation) |
| `ExternalSecret` | Pulls secrets from the secret manager into a Kubernetes `Secret` |
| `Deployment` | 2+ replicas, non-root, liveness/readiness on actuator probes |
| `Service` | ClusterIP on port 80 → 8080 |
| `HorizontalPodAutoscaler` | 2–6 replicas at 70% CPU |

Every component is safe to run on several replicas at once: the scheduler, expiry job, recap job and sweeper
all write under optimistic locking, so two pods working on the same goal can't both commit.

## Secrets

Secrets have **no defaults** in config, so a missing secret fails at startup instead of silently using a dev value.
In the cluster they come from Vault, AWS Secrets Manager or GCP Secret Manager through the
[External Secrets Operator](https://external-secrets.io):

| Env var | Secret manager path | Used for |
|---|---|---|
| `DB_PASSWORD` | `coach/database#password` | Postgres |
| `REDIS_PASSWORD` | `coach/redis#password` | Redis |
| `ANTHROPIC_API_KEY` | `coach/anthropic#apiKey` | Claude |
| `FCM_SERVICE_ACCOUNT_JSON` | `coach/firebase#serviceAccountJson` | FCM HTTP v1 OAuth |

Rotating a value in the secret manager updates the `Secret` within `refreshInterval` (1 h); pods pick it up on
restart. For a throwaway cluster, set `externalSecret.enabled=false` and `existingSecret=<your secret>`.

## Production checklist

- [ ] Replace `ddl-auto: update` with Flyway migrations
- [ ] Add authentication (validate Firebase/Supabase JWTs; derive `userId` from the token, not the path)
- [ ] Managed Postgres with backups; Kafka with replication factor 3; set `coach.queue.kafka.partitions`
- [ ] Alert on dead-letter topics (`coach.commands.DLT`, `coach.events.DLT`) and on sweeper recoveries
- [ ] Dashboards: push → action rate, plan fallback rate, AI latency and errors, nudges per minute
- [ ] Set a per-user AI budget and enable prompt caching
