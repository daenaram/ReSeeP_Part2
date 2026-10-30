# ReSeeP backend

A small JAX-RS + JPA backend for the ReSeeP recipe/pantry tracker, deployed
as a WAR on Payara. It's built to be called by the `index.html` frontend
without any changes on that side, once you point `API_BASE` at this app.

## Software required

- JDK 21 (or 25)
- Apache Maven 3.9.x
- Payara Server 6.2025.11 (JDK 21) or 7.2026.2 (JDK 25)
- MySQL Connector/J 9.7, placed in `<Payara>/glassfish/domains/domain1/lib`

## 1. Point the persistence unit at your JDBC resource

Open `src/main/resources/META-INF/persistence.xml` and confirm:

```xml
<jta-data-source>jdbc/ReSeePPool</jta-data-source>
```
There is no separate SQL script to run by hand — the three tables
(`ingredient`, `recipe`, `recipe_ingredient`) and the sample data are created
automatically the first time the app deploys, by `DbInitializer.java` (see
below). If your database already has empty tables from an earlier attempt,
that's fine — `DbInitializer` only inserts sample data if the `ingredient`
table is currently empty, so it won't duplicate anything.

## 2. Build

```
mvn clean package
```

This produces `target/reseep.war`.

## 3. Deploy

Deploy `reseep.war` to Payara — either drag it into the admin console
(Applications > Deploy) or:

```
asadmin deploy --force=true target/reseep.war
```

The app is served at `http://localhost:8080/reseep`, so the API root is:

```
http://localhost:8080/reseep/api
```

Watch `server.log` for the line `[DbInitializer] Schema check complete.` to
confirm the tables were created (or already existed) without errors.

## 4. Point the frontend at it

In `index.html`, set:

```js
const API_BASE = "http://localhost:8080/reseep/api";
```

Reload the page — the status line under the tabs should say
"Connected to …". All data comes from the server; there is no offline
sample mode. If the server can't be reached, the page shows a red banner
with **Try again**, refuses changes (each failed save says why and leaves the
screen as it was), and reloads by itself once the live-update socket
reconnects.

## Resetting the data

Visiting `http://localhost:8080/reseep/reset` in a browser clears every row
from all three tables (see `ResetServlet.java`). Useful before a clean demo
run. If you redeploy afterwards, `DbInitializer` will reseed the sample data
automatically, since it'll find the `ingredient` table empty again.

## Endpoints

| Method | Path                | Description                                   |
|--------|---------------------|------------------------------------------------|
| GET    | `/api/recipes`      | List all recipes with their ingredient lines   |
| GET    | `/api/recipes/{id}` | Get one recipe                                 |
| POST   | `/api/recipes`      | Create a recipe                                |
| PUT    | `/api/recipes/{id}` | Replace a recipe's fields and ingredient list  |
| DELETE | `/api/recipes/{id}` | Delete a recipe                                |
| GET    | `/api/pantry`       | List all pantry ingredients                    |
| POST   | `/api/pantry`       | Add a new pantry ingredient                    |
| PUT    | `/api/pantry/{id}`  | Update a pantry ingredient's quantity on hand  |
| POST   | `/api/login`        | Sign in: `{ username, password }` → `{ id, username, role }` |
| GET    | `/api/shopping`     | List all shopping list items (needed and bought) |
| POST   | `/api/recipes/{id}/plan` | Work out what's missing for this recipe and add it to the shopping list. Body: `{ userId }` |
| POST   | `/api/shopping/{id}/bought` | Mark an item bought. Body: `{ userId }`. Publishes an `ItemPurchased` event -- see "Message broker" below |

All request/response bodies are JSON. Validation is intentionally simple:
a blank recipe/ingredient name, a negative quantity, or an unknown
`ingredientId` on a recipe line all return `400 Bad Request` with a short
JSON error message. Requesting a recipe/ingredient id that doesn't exist
returns `404 Not Found`.

## Test accounts

| Username  | Password     | Role    | Web views                                   |
|-----------|--------------|---------|---------------------------------------------|
| `cook`    | `cook123`    | Cook    | Recipes, Pantry, Meal plan, Shopping list   |
| `shopper` | `shopper123` | Shopper | Shopping list, Pantry                     |

The web client signs in with `POST /api/login` (`{ username, password }` →
`{ id, username, role }`). The signed-in user is
kept in `sessionStorage`, so it lasts until the tab is closed. Hiding views
by role is only a convenience in the UI; the API is responsible for
enforcing roles.

## Live updates (WebSocket)

Open web pages connect to:

```
ws://localhost:8080/reseep/ws/updates
```

After every successful (committed) create/update/delete on recipes or the
pantry, the server pushes one small message to every connected page:

```json
{ "type": "pantry-updated", "action": "updated", "id": 3 }
{ "type": "recipe-updated", "action": "deleted", "id": 7 }
```

`type` is `pantry-updated` or `recipe-updated`; `action` is `created`,
`updated` or `deleted`. The page treats the message as a hint and re-fetches
`/recipes` and `/pantry` over REST, so REST remains the source of truth.

Broadcasts are fired as CDI events observed at
`TransactionPhase.AFTER_SUCCESS` (`LiveUpdateBroadcaster.java`), so a page
never hears about a change before it is committed, and a rolled-back write
sends nothing.

If the connection drops, the page shows "Live updates lost — retrying" and
reconnects with exponential backoff (1s, 2s, 4s … capped at 10s). On
reconnect it reloads the full current state, since any changes made while
disconnected were never pushed.

**Try it:** open `http://localhost:8080/reseep/` in two browser windows side
by side, change a pantry quantity in one, and the other updates (including
the "Ready to cook / N missing" badges) without a refresh.

To see recovery, run `asadmin disable reseep` while a page is open. The
status line switches to "Live updates lost". Run `asadmin enable reseep`
and, within a few seconds, the page reconnects on its own, shows
"Reconnected — showing latest data" and reloads everything. To show that it
catches up on missed changes, edit something with curl right after
re-enabling, before the page's next retry fires.

## Message broker (RabbitMQ)

Marking a shopping item bought doesn't update the pantry directly. Instead,
`ShoppingListService.markBought()` publishes an `ItemPurchased` event to
RabbitMQ and returns immediately. A completely separate program --
`reseep-consumer`, not part of this WAR and never deployed to Payara --
picks that event up, adds the bought quantity to the pantry, writes a row
to `purchase_log`, and tells this web app to push a live update to
connected browsers. This is the "advanced integration" piece of the brief:
two independently running services connected only by a queue, not a direct
method call.

### 1. Start RabbitMQ

From the project root:

```
docker compose up -d
```

This starts RabbitMQ with its management UI at `http://localhost:15672`
(login `reseep` / `reseep`), where you can watch the `item_purchased_queue`
queue fill up and drain, and manually publish messages for the demo below.

### 2. Make sure the web app can reach it

No extra setup needed on the Payara side -- the RabbitMQ client library is
bundled straight into `reseep.war` (unlike the MySQL driver, nothing goes
into Payara's own `lib` folder). `EventPublisher` connects to
`localhost:5672` by default and logs a warning (without crashing the app)
if RabbitMQ isn't running yet.

### 3. Run the consumer

The consumer is a separate Maven project in `reseep-consumer/`. It needs
its own database credentials as environment variables (never hardcode a
password into source that gets committed):

```
cd reseep-consumer
set DB_PASSWORD=$X9$2FHzGEaX4aMzsy$8%NxK      # PowerShell: $env:DB_PASSWORD="..."
mvn exec:java
```

Leave this running in its own terminal window. You should see:

```
[Consumer] Waiting for ItemPurchased events on 'item_purchased_queue'. Ctrl+C to stop.
```

Other environment variables (`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`,
`RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD`,
`WEBAPP_BASE_URL`) all have sensible defaults matching this project's setup
and only need overriding if yours differs.

### 4. See it work end to end

1. `POST /api/recipes/1/plan` with `{"userId":1}` to put something on the
   shopping list.
2. `POST /api/shopping/{id}/bought` with `{"userId":2}`.
3. Watch the consumer's terminal -- within moments it logs the event being
   received, the pantry being updated, and being acked.
4. `GET /api/pantry` to confirm the ingredient's `qtyOnHand` went up by the
   bought quantity.

### Demonstrating failure and retry

Stop the consumer, set the demo flag, and restart it:

```
set SIMULATE_FAILURE_ONCE=true
mvn exec:java
```

Mark an item bought. The consumer logs a simulated failure and **nacks**
the message with `requeue=true` instead of acking it -- watch the
management UI's queue page and you'll see the message go back to "Ready"
rather than disappearing. Because `SIMULATE_FAILURE_ONCE` only fires once
(a one-shot flag per consumer run), RabbitMQ redelivers that same message a
moment later, the consumer processes it normally this time, and the pantry
updates. This demonstrates the brief's "failure triggers a retry"
requirement concretely, on command, rather than relying on a real failure
happening to occur during recording.

### Demonstrating a duplicate/repeated event

In the RabbitMQ management UI, open the `item_purchased_queue` queue and
use "Publish message" to manually re-send the **exact same JSON** the
consumer already processed earlier (copy it from the consumer's terminal
log, where it printed `[Consumer] Received: {...}` -- same `eventId` and
all). The consumer logs:

```
[Consumer] Event <id> was already processed -- skipping (idempotent).
```

and acks it without touching the pantry again, because `processed_events`
already has a row for that `eventId`. This demonstrates the brief's "a
repeated event is ignored" requirement.

## Response-time test (non-functional requirement)

**Operation:** the Cook changes a pantry quantity (`PUT /api/pantry/{id}`), a
full write through REST → service → MySQL that also broadcasts a
`pantry-updated` live update.

| Id | Requirement | Target |
|----|-------------|--------|
| T1 | HTTP response time | 95th percentile ≤ 1000 ms |
| T2 | Live update reaches another open client (from sending the request to the WebSocket event arriving) | 95th percentile ≤ 1000 ms |

1 second is the usual limit for a response to still feel uninterrupted; for
the shared shopping list it means the other user sees a change while they're
still looking at the screen.

With Payara running, from the repository root (needs only a JDK, no build):

```
java perf/ResponseTimeTest.java                                  # 50 requests to localhost
java perf/ResponseTimeTest.java http://localhost:8080/reseep 100 # custom URL / count
```

It sends 5 warm-up requests, then the measured ones one after another, while
a second WebSocket client listens for each update. It alternates the first
pantry ingredient between its quantity and +1 and restores the original at
the end, so it leaves no net change (open web pages will refresh on each
change while it runs). It prints min/median/mean/p95/max with PASS/FAIL,
exits 0 only if both targets are met, and writes the raw timings to
`perf/results/response-times-<timestamp>.csv`.

**Result (30 Oct 2026):** Payara 7 on Windows 11 / JDK 21 on localhost, AWS
RDS MySQL database, 1 client, 50 sequential requests.

| | min | median | mean | p95 | max | Result |
|---|---|---|---|---|---|---|
| T1 HTTP response (ms) | 164 | 333 | 333 | 440 | 468 | PASS |
| T2 Live update (ms) | 157 | 325 | 326 | 435 | 460 | PASS |

The live update arrives slightly *before* the HTTP response because it is
sent as soon as the transaction commits, while the response is still being
written. Almost all of the time is the round trip to the remote database;
this is a single-user test, not a load test.

## Known limitations

- No authentication — anyone who can reach the API can read/write everything.
- `PUT /recipes/{id}` replaces the whole ingredient list rather than
  patching individual lines; simplest correct behaviour for this scope.
- CORS is wide open (`Access-Control-Allow-Origin: *`) in `CorsFilter.java`,
  which is convenient for local development/marking but not something
  you'd ship as-is.
- Schema creation and seeding happen in application code (`DbInitializer`)
  rather than a proper migration tool — fine for coursework, but a real
  deployment would use something like Flyway instead.
- `/reset` has no confirmation step and no access control — anyone who
  knows the URL can wipe the data instantly. Acceptable for a local dev
  convenience, not something to expose publicly.
- `/api/internal/notify-pantry-change` is unauthenticated and undocumented
  for external use — it exists purely so the separate consumer process can
  trigger a WebSocket broadcast after it updates the pantry. Same dev-grade
  status as `/reset`.
- The consumer's database credentials are supplied via environment
  variables, not committed to source — but it still connects with a plain
  username/password over JDBC, same trust model as the web app's own JDBC
  pool, not a separately secured service account.