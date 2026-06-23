# OKX Market Data Service

A Spring Boot service that proxies OKX public market data to a browser client. Clients never call OKX directly — all data flows through this backend.

---

## How to run

**Prerequisites:** Java 17+, Maven 3.8+

```bash
mvn spring-boot:run
```

Server starts at `http://localhost:8080`. Open that URL in a browser to use the built-in test UI.

To run from IntelliJ: open the project folder (the one containing `pom.xml`), let Maven sync, then run `OkxTradingApplication.java`.

---

## Configuration

All URLs and credentials live in `src/main/resources/application.yml`:

```yaml
okx:
  rest:
    base-url: https://www.okx.com
    tickers-path: /api/v5/market/tickers
    ticker-path: /api/v5/market/ticker
  websocket:
    public-url: wss://ws.okx.com:8443/ws/v5/public

auth:
  username: admin
  password: admin123
```

---

## APIs

All market data endpoints require a valid session token passed as `X-Session-Token` header. The token is obtained from the login endpoint.

---

### POST `/api/auth/login`

Validates credentials and returns a session token.

**Request:**
```json
{ "username": "admin", "password": "admin123" }
```

**Success (200):**
```json
{
  "status": "success",
  "message": "Login successful",
  "user": "admin",
  "token": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Failure (401):**
```json
{ "status": "error", "message": "Invalid username or password" }
```

---

### POST `/api/auth/logout`

Invalidates the current session.

```
Header: X-Session-Token: <token>
```

---

### GET `/api/market/top-pairs`

Returns the top 20 SPOT pairs by 24h quote volume. Calls OKX's `/api/v5/market/tickers?instType=SPOT`, sorts by `volCcy24h`, and returns the top 20.

```
Header: X-Session-Token: <token>
```

**Response (200):** Array of 20 objects:
```json
[
  {
    "instId": "BTC-USDT",
    "lastPrice": 67420.1,
    "open24h": 66100.0,
    "high24h": 68100.0,
    "low24h": 66800.0,
    "volumeCcy24h": 1243000000.0,
    "change24hPct": 2.00,
    "timestamp": 1718000000000
  }
]
```

`change24hPct` is computed server-side as `((last - open24h) / open24h) * 100`.

---

### POST `/api/market/orderbook/{instId}`

Opens a WebSocket connection to OKX's `books` channel for the given pair and begins streaming order book updates.

```
Header: X-Session-Token: <token>
```

**Example:** `POST /api/market/orderbook/BTC-USDT`

**Response (200):**
```json
{
  "status": "subscribed",
  "channel": "books",
  "instId": "BTC-USDT",
  "stompTopic": "/topic/orderbook/BTC-USDT"
}
```

After this call, connect a STOMP client to `ws://localhost:8080/ws` and subscribe to `/topic/orderbook/BTC-USDT` to receive live updates. Each message contains up to 400 bid/ask levels — the UI displays the top 15.

---

### DELETE `/api/market/orderbook/{instId}`

Closes the OKX WebSocket connection for the pair and stops forwarding messages.

```
Header: X-Session-Token: <token>
```

**Response (200):**
```json
{ "status": "unsubscribed", "instId": "BTC-USDT" }
```

---

## Single-session enforcement

Each username is allowed exactly one active session at a time.

On login, `SessionService` generates a UUID token and stores it in a `username → token` map. If that username already has an active token, the old one is removed from the map before the new one is stored — the previous session is immediately dead.

Every protected endpoint reads the `X-Session-Token` header and calls `SessionService.validate()`, which checks two things: the token exists in the index, and it still matches the current token for that user. If a second login has happened since the token was issued, the first check passes but the second fails, and the request gets a 401.

This means: if a user logs in on a second browser tab, any subsequent request from the first tab is rejected. There is no grace period or notification — the old session is gone the moment the new login succeeds.

---

## Assumptions

- One WebSocket connection per trading pair is opened on the server side. Multiple clients subscribing to the same pair share the same OKX connection.
- OKX public endpoints do not require an API key. No authentication toward OKX is implemented.
- The `books` WebSocket channel returns up to 400 levels. The UI shows the top 15; the full payload is available on the STOMP topic if needed.
- 24h change % is calculated from `open24h` (the price at the start of the 24h rolling window) and `last` (the most recent traded price), both provided by OKX.
- Session tokens are stored in memory. Restarting the server invalidates all sessions.

---

## Known limitations

- Auth is a dummy implementation. Credentials are stored in plaintext in a config file. Do not use this in production.
- No token expiry. Sessions live until the user logs out or the server restarts.
- A dropped OKX WebSocket connection is detected and reconnected by a 30-second health check, so there is a potential gap of up to 30 seconds in the stream after a disconnect.
- Subscribing to a large number of pairs simultaneously opens one OKX connection per pair. There is no connection pooling or multiplexing.
- No persistence. Active subscriptions and sessions are lost on server restart.

---

## Project structure

```
src/main/java/com/okx/
├── OkxTradingApplication.java
├── config/
│   ├── AppConfig.java                RestTemplate and ObjectMapper beans
│   └── WebSocketConfig.java          STOMP broker and /ws endpoint
├── controller/
│   ├── AuthController.java           login / logout
│   └── MarketDataController.java     top-pairs, orderbook subscribe/unsubscribe
├── model/
│   ├── AuthRequest.java
│   ├── Ticker.java                   includes computed change24hPct
│   ├── OrderBook.java
│   ├── OkxApiResponse.java           OKX REST response envelope
│   └── WsSubscription.java           OKX WebSocket subscribe message format
├── service/
│   ├── SessionService.java           single-session-per-user enforcement
│   ├── OkxRestService.java           calls OKX REST API
│   └── OkxWebSocketManager.java      manages OKX WS connections + reconnect
└── websocket/
    └── OkxWebSocketClient.java       low-level OKX WebSocket client
```