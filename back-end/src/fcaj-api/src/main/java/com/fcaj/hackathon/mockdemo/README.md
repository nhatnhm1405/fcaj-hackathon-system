# Local AI job mock

This package implements the interrupted local demo with three authenticated APIs.
Results are synthetic and labelled `LOCAL_MOCK`; no repository is fetched, no
model is called, no AWS message is sent and no official Score/RoundResult is written.

## Run

Configure the existing local business database/auth as usual. Use a judge assigned
to a submission or a coordinator granted that exact event; start its JUDGING timer.
The mock does not create accounts, assignments, competition data or demo credentials.
From `back-end/src/fcaj-api`:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-21.0.10'
./mvnw.cmd -Pmock-demo spring-boot:run '-Dspring-boot.run.profiles=local,mock-demo'
```

Both selections matter: Maven `-Pmock-demo` includes the H2 runtime dependency;
Spring `local,mock-demo` enables the APIs/worker. The application binds only
`127.0.0.1`. Normal builds/profiles do not activate mock components. AWS combined
with mock-demo, a public bind address or a remote/unapproved mock store URL fails
startup before clients connect.

The business datasource is still selected by `local`; its existing startup and
reference/bootstrap/demo policies still apply. Mock jobs have their own H2 file
at `target/mock-demo/ai-jobs.mv.db`. The `db/mock-demo` migration runs only on this
private store through its own Flyway instance. It does not run MySQL V1 on H2 or
change the application business schema. Do not delete/clean `target` if you need
to keep local job history; this is demo persistence, not production retention.

## API flow

Authenticate using the existing login endpoint. Cookie-authenticated POSTs still
need the usual XSRF cookie/header; Bearer tooling follows existing security rules.

| Request | Expected response |
|---|---|
| `POST /api/ai/submissions/{submissionId}/jobs` | 202 with jobId, status PENDING_DISPATCH, UTC timestamps and mode LOCAL_MOCK |
| `GET /api/ai/jobs/{jobId}` | Current status, resultAvailable and safe errorCode |
| `GET /api/ai/jobs/{jobId}/result` | Synthetic advisory output after SUCCEEDED; 409 while pending or FAILED |

The worker ticks every 5 seconds. On one tick it dispatches one pending job; a
later tick completes one queued job. Completion is intentionally delivered twice
with the same message UUID; logs show APPLIED then DUPLICATE. API responses omit
requester identity, repo URL, internal revision/store refs and raw messages.

`inputRevision` is a synthetic per-job SHA-256 marker. It is **not** a real Git
commit, repository/rubric snapshot or production input manifest. The mock result
has empty criteria insights and no score recommendations. Each POST creates a
new job; POST idempotency/header semantics remain a contract decision.

For a failure demo set `APP_AI_JOBS_MOCKOUTCOME=FAILED` before startup (Spring's
canonical environment form for `app.ai.jobs.mock-outcome`). For an offline-worker
demo set `APP_AI_JOBS_MOCKWORKER_ENABLED=false`; pending jobs remain pending and
can be resumed by restarting with the worker enabled. No timeout is invented.

Alternatively use explicit Spring run arguments, which avoid environment naming:

```powershell
./mvnw.cmd -Pmock-demo spring-boot:run '-Dspring-boot.run.profiles=local,mock-demo' '-Dspring-boot.run.arguments=--app.ai.jobs.mock-outcome=FAILED'
```

The worker completion/dispatch methods are internal Java calls. There is no public
debug route accepting completion payloads or a client-chosen requester/revision.

## Authorization and persistence

- Coordinator: current approved account and an exact event grant; a global/null
  coordinator grant alone is insufficient. Creation requires judging open.
- Judge: current approved account, JUDGE role and active assignment matching the
  submission's round/track. History is restricted to its original requester.
- Reads recheck current account/grants/assignment. Closing judging retains history;
  revoking the applicable role/assignment blocks the next request.
- Unknown and unauthorized job reads use 404. Wrong role/scope creation uses 403;
  closed judging and unavailable results use safe 409 codes.
- Completion locks the job row and atomically commits the processed-message ledger
  and terminal state. Stale revision or conflicting message/outcome cannot overwrite
  a job. Late dispatch never moves a terminal job back to QUEUED.
- Old and new evaluations have independent job IDs and rows. There is no shared
  latest-result pointer that a late result can overwrite.

The private store uses a job row/dispatch marker to simulate pending delivery; it
does **not** implement a production outbox/SQS delivery transaction with the core
database, a repository collector, durable artifact storage or result validation
against the external worker contract. Those remain separate production tasks.

## Verification

```powershell
./mvnw.cmd '-Dspring.profiles.active=test' test
```

New tests cover API/security integration, current event/assignment/ownership,
CSRF, offline pending, duplicate/stale/conflict/late completion, concurrent
redelivery, transaction rollback and re-opening a file store with its ledger.
Tests create synthetic records in isolated H2 databases; they do not use the real
dump, local MySQL or AWS. See the [implementation report](../../../../../../../../../../docs/report/local-mock-job-completion-2026-10-27.md).
