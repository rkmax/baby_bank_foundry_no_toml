# Baby Bank Java Backend

This module adds a small Java HTTP backend for the `baby_bank` demo contract.

It exposes:

- contract metadata and ABI loaded from `out/baby_bank.sol/baby_bank.json`
- audit findings mirrored from the frontend dataset
- an in-memory runtime that simulates the signup, deposit, withdraw, and block
  advance flows

## Requirements

- Java 17+
- `curl` for the smoke test

No external build tool is required. The `run.sh` script uses `javac` from `PATH`,
`JAVA_HOME`, `JAVAC_BIN`, or a local OpenJDK 17 fallback when available.

## Run

From the repository root:

```sh
./java-backend/run.sh
```

The service listens on `http://127.0.0.1:8080` by default.

Optional environment variables:

- `BABY_BANK_PORT` to change the port
- `BABY_BANK_HOST` to change the bind address
- `BABY_BANK_REPO_ROOT` to override the repository root used to locate the
  contract source and Foundry artifact

## Endpoints

- `GET /api/health`
- `GET /api/contract`
- `GET /api/findings`
- `GET /api/state`
- `POST /api/signup`
- `POST /api/deposit`
- `POST /api/withdraw`
- `POST /api/advance-blocks`
- `POST /api/reset`

## Example Requests

```sh
curl -s http://127.0.0.1:8080/api/contract
curl -s http://127.0.0.1:8080/api/state
curl -s -H 'Content-Type: application/json' \
  -d '{"alias":"alice"}' \
  http://127.0.0.1:8080/api/signup
curl -s -H 'Content-Type: application/json' \
  -d '{"confirmAlias":"alice","lockBlocks":10,"amountEth":1.5}' \
  http://127.0.0.1:8080/api/deposit
curl -s -H 'Content-Type: application/json' \
  -d '{"amount":10}' \
  http://127.0.0.1:8080/api/advance-blocks
curl -s -H 'Content-Type: application/json' \
  -d '{}' \
  http://127.0.0.1:8080/api/withdraw
```

## Smoke Test

```sh
./java-backend/smoke-test.sh
```
