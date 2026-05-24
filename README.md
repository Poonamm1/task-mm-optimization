# task-mm-optimization

> A learning-archive Spring Boot worker service that scores and selects warehouse
> **movement tasks** (think: "what should the picker do next?") using a weighted
> scoring model with an optional constrained selector backed by **Google OR-Tools** (CP-SAT).

This project started as a learning POC. It was never deployed to prod — it's published
here as a personal reference / portfolio piece. All identifiers, credentials and
sample data have been dummified.

---

## ✨ What it does

The service is a background **job worker** that consumes optimization jobs from a SQL Server
queue and writes ranked move recommendations back to a results table.

```
┌──────────────┐   poll    ┌────────────────┐   score   ┌───────────────────┐
│ optimizer_   │ ────────► │ JobService     │ ────────► │ ScoringMove       │
│ jobs (queue) │           │ (Spring @Tx)   │           │ Optimizer         │
└──────────────┘           └────────────────┘           └───────────────────┘
                                 │                              │
                                 │ optional                     │ scores +
                                 ▼                              ▼ ordered IDs
                          ┌────────────────┐            ┌───────────────────┐
                          │ IMoveSelector  │  top-K     │ ActiveMoveFilter  │
                          │ (Greedy / OR-  │ under      │ (drop non-active) │
                          │ Tools CP-SAT)  │ constraints│                   │
                          └────────────────┘            └───────────────────┘
                                                                │
                                                                ▼
                                                       ┌───────────────────┐
                                                       │ optimizer_results │
                                                       │ (TTL'd upsert)    │
                                                       └───────────────────┘
```

**Pipeline (per job):**

1. **Claim** the next pending job (`SELECT … WITH (READPAST, UPDLOCK, ROWLOCK)`) and flip it to `RUNNING` in the same transaction.
2. **Parse** the request payload; pick a weights profile (`coldStart` vs `normal`) if the caller didn't supply one.
3. **Score** every candidate `Move` with `ScoringMoveOptimizer`.
4. **(Optional) Select** top-K under constraints (e.g., "no more than 1 congested aisle in the chosen set") via Greedy or OR-Tools.
5. **Filter** out moves that are no longer active (`OPEN`/`ASSIGNED`/`CHOSEN-for-this-user`).
6. **Upsert** the result with metadata (model version, weights profile, timings, counts) and mark the job `DONE` (or `FAILED` after `max-retries`).

---

## 🧠 Scoring model

Each candidate move is scored as a weighted combination of normalized features:

| Feature       | Intuition                                                |
|---------------|----------------------------------------------------------|
| `wPriority`   | Lower priority number = more urgent                      |
| `wAge`        | Older "OPEN" activity = more urgent                      |
| `wType`       | Bias toward the user's assigned move types               |
| `wDistance`   | Closer to the user's last scan = cheaper to do next      |
| `wCongestion` | Penalize aisles with high concurrent activity            |
| `sameAreaBonus` | Tiny nudge for staying in the same warehouse zone       |

Four built-in profiles live in [`Weights.java`](src/main/java/com.dummy.move.nim.uwms.mm/moves/models/Weights.java):

- **`coldStart`** — no last-scan known; lean on priority + age, ignore distance.
- **`normal`** — standard run; distance matters again.
- **`atlasFppJump`** — activity recency outweighs distance.
- **`replen`** — replenishment; walk cost matters, steady congestion penalty.

---

## 🛠 Tech stack

- **Java 17 / 21** (build targets release 21; runtime needs ≥17)
- **Spring Boot 3.3** (`spring-boot-starter`, `spring-boot-starter-data-jpa`)
- **SQL Server** via `mssql-jdbc`
- **Google OR-Tools** (`ortools-java` 9.14) for the CP-SAT selector
- **Jackson** for JSON payloads
- **JUnit 5** for tests
- **Maven** with `fmt-maven-plugin` (Google Java Format) on `verify`

---

## 📁 Project layout

```
src/main/java/com.dummy.move.nim.uwms.mm/
├── UwmsMMOptimizerApplication.java   # Spring Boot entrypoint (@EnableScheduling)
├── common/                           # cross-cutting beans
├── configurations/                   # @ConfigurationProperties (worker, weights, selector)
├── moves/
│   ├── db/                           # JPA entities (jobs / results / penalty config)
│   ├── interfaces/                   # IMoveOptimizer, IMoveSelector, CongestionProvider
│   ├── models/                       # Move, OptimizerRequest, OptimizerResult, Weights
│   ├── repositores/                  # Spring Data repos & DAOs
│   ├── services/                     # JobService, JobRunner, ScoringMoveOptimizer,
│   │                                 # GreedyMoveSelectorI, OrtoolsMoveSelectorI, ...
│   └── utils/                        # TimeUtil
src/main/resources/
└── application.properties            # all knobs (dummy DB creds; override in real envs)
src/test/java/com/dummy/...           # JUnit 5 tests
```

---

## ⚙️ Configuration

All runtime knobs live under the `optimizer.*` prefix in
[`application.properties`](src/main/resources/application.properties).

| Key                                          | Default                   | What it does                                   |
|----------------------------------------------|---------------------------|------------------------------------------------|
| `optimizer.worker.ms`                        | `500`                     | Scheduler poll interval (ms)                   |
| `optimizer.worker.batch-size`                | `10`                      | Jobs per tick                                  |
| `optimizer.worker.max-retries`               | `3`                       | Failure cap before job → `FAILED`              |
| `optimizer.ttl.seconds`                      | `90`                      | Result freshness window                        |
| `optimizer.selector.use-ortools`             | `false`                   | Flip on to enable CP-SAT                       |
| `optimizer.selector.k`                       | `10`                      | Top-K passed to the selector                   |
| `optimizer.selector.congested-aisle-threshold` | `0.75`                  | Aisle ≥ this congestion = "congested"          |
| `optimizer.selector.congested-aisle-max`     | `1`                       | Max congested aisles allowed in the picked set |
| `optimizer.weights.normal.*`                 | (see file)                | Per-feature weights for the `normal` profile   |
| `optimizer.weights.coldStart.*`              | (see file)                | Per-feature weights for the `coldStart` profile|
| `optimizer.results.filterNonActive`          | `true`                    | Drop moves whose status is no longer active    |
| `optimizer.model-version`                    | `v5a-2025-08-23`          | Stamped into result metadata                   |

> 🔐 The committed DB URL/username/password are **dummy placeholders**.
> For any real use, override them via environment variables, a Spring profile,
> or a secrets manager — **never** commit real credentials.

---

## 🚀 Running it locally

### Prerequisites
- JDK **17 or newer** (Maven targets release 21)
- Maven 3.9+
- A SQL Server instance with the optimizer schema, **or** swap the datasource for H2 to play around

### Build
```bash
mvn clean verify
```

### Run
```bash
mvn spring-boot:run
# or
java -jar target/task-mm-optimization-1.0-SNAPSHOT.jar
```

### Override the dummy DB creds
```bash
SPRING_DATASOURCE_URL="jdbc:sqlserver://your-host:1433;databaseName=your_db" \
SPRING_DATASOURCE_USERNAME="your_user" \
SPRING_DATASOURCE_PASSWORD="your_password" \
mvn spring-boot:run
```

### Run tests
```bash
mvn test
```

---

## 🧪 Selector implementations

| Impl          | Class                       | When to use                                              |
|---------------|-----------------------------|----------------------------------------------------------|
| `greedy`      | `GreedyMoveSelectorI`       | Fast, score-only. Default. Great for most live traffic.  |
| `ortools`     | `OrtoolsMoveSelectorI`      | Constrained top-K via CP-SAT. Honors aisle caps, etc.    |
| `auto`        | (chosen by `JobService`)    | Pick per-request based on candidate count / congestion.  |

The OR-Tools selector is time-boxed (`solverMaxTimeSeconds`, default `0.05s`) so a worst-case
solve still fits inside a sub-second job budget.

---

## 📝 Notes

- This is a **learning archive**. The dependency `com.dummy.move.nim:uwms-mm-domain` is a
  placeholder — the original artifact lived in a private registry and is not resolvable from
  Maven Central. The Maven build will not succeed end-to-end without providing a stub for
  that domain artifact (or restoring the original coordinates if you have access).
- `.idea/`, `target/`, and `*.iml` are gitignored.
- All `walmart` / internal references have been replaced with `dummy` throughout the source.

---

## 📜 License

No license specified — this is a personal learning archive. Please don't redistribute
without asking.
