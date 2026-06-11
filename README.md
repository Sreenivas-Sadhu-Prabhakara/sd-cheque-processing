# Cheque Processing

BIAN Service Domain microservice — **Phase 2a DEEP build** (graduated from the golden template; see `.bian-graduated`). This is the platform's **check-clearance** domain — added to the catalog as BIAN's `Cheque Processing` SD (Payments business domain).

| | |
|---|---|
| **Business Area** | Operations and Execution |
| **Business Domain** | Payments |
| **Functional Pattern** | Process |
| **Control Record** | Cheque Transaction Procedure |
| **K8s Namespace** | `bian-operations` |

## The clearing state machine

```
LODGED ──present──▶ PRESENTED ──clear────▶ CLEARED   (beneficiary credit emitted)
  │                     └────────return──▶ RETURNED  (reason mandatory)
  └──stop──▶ STOPPED   (only BEFORE presentment — after that the cheque
                        is with the clearing house and cannot be stopped)
```

- **Lodgement validation**: positive amount, ISO currency, 6+-digit cheque number, and *no self-deposit* (drawer ≠ beneficiary).
- **`cheque.cleared` carries the beneficiary credit instruction** — the flagship payments choreography. Current/Savings Account post it as a `cheque-credit` (HTTP call today, Kafka consumer when the backbone is live).
- **`cheque.lodged` feeds Fraud Detection** (high-value lodgement signal).
- Terminal states accept no further transitions; every illegal transition is a `409 ILLEGAL_TRANSITION`.

## API & contracts (owned by this repo)

- REST: [`api/openapi.yaml`](api/openapi.yaml) · Events: [`api/events.yaml`](api/events.yaml)
- Base: `/v1/cheque-transaction-procedure`
- `POST /initiate` (lodge) · `POST /{id}/present` · `POST /{id}/clear` · `POST /{id}/return` · `PUT /{id}/control` (`stop`) · `GET /{id}/retrieve`

```bash
mvn spring-boot:run
CR=/v1/cheque-transaction-procedure
ID=$(curl -s -X POST localhost:8080$CR/initiate -H 'content-type: application/json' \
     -d '{"chequeNumber":"123456","drawerAccountRef":"CA-D","beneficiaryAccountRef":"CA-B","amountMinor":50000}' | jq -r .chequeId)
curl -s -X POST localhost:8080$CR/$ID/present
curl -s -X POST localhost:8080$CR/$ID/clear     # → emits cheque.cleared with the credit instruction
```

## Persistence

In-memory port/adapter. **Postgres ready to hydrate, not wired**: [`db/schema.sql`](db/schema.sql) (+ seed). The no-self-deposit and returned-has-reason rules are DB CHECK constraints too.

## Tests

`mvn verify` — lodgement validation, the full happy path with event assertions, returns, and every state-machine guard (stop-after-presentment, clear-before-present, terminal immutability), plus a boot/API journey.
