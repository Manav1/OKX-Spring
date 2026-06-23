package com.okx.controller;

import com.okx.model.Ticker;
import com.okx.service.OkxRestService;
import com.okx.service.OkxWebSocketManager;
import com.okx.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MarketDataController {

    private final OkxRestService      restService;
    private final OkxWebSocketManager wsManager;
    private final SessionService      sessionService;

    /**
     * API 1 — Top 20 most traded SPOT pairs by 24h volume.
     * Requires valid session token in X-Session-Token header.
     *
     * GET http://localhost:8080/api/market/top-pairs
     */
    @GetMapping("/top-pairs")
    public ResponseEntity<?> getTopPairs(
            @RequestHeader(value = "X-Session-Token", required = false) String token) {

        try {
            sessionService.validate(token);
        } catch (SecurityException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }

        log.info("GET /api/market/top-pairs");
        List<Ticker> pairs = restService.getTopTradedPairs();
        if (pairs.isEmpty()) {
            return ResponseEntity.status(503).build();
        }
        return ResponseEntity.ok(pairs);
    }

    /**
     * API 2a — Start real-time order book stream (books channel, top 15 levels).
     * Requires valid session token in X-Session-Token header.
     *
     * POST http://localhost:8080/api/market/orderbook/BTC-USDT
     */
    @PostMapping("/orderbook/{instId}")
    public ResponseEntity<?> subscribeOrderBook(
            @PathVariable String instId,
            @RequestHeader(value = "X-Session-Token", required = false) String token) {

        String username;
        try {
            username = sessionService.validate(token);
        } catch (SecurityException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }

        String pair = instId.toUpperCase();
        log.info("POST /api/market/orderbook/{} by user={}", pair, username);
        wsManager.subscribeOrderBook(pair);

        return ResponseEntity.ok(Map.of(
                "status",     "subscribed",
                "channel",    "books",
                "instId",     pair,
                "stompTopic", "/topic/orderbook/" + pair
        ));
    }

    /**
     * API 2b — Stop the order book stream.
     * Requires valid session token in X-Session-Token header.
     *
     * DELETE http://localhost:8080/api/market/orderbook/BTC-USDT
     */
    @DeleteMapping("/orderbook/{instId}")
    public ResponseEntity<?> unsubscribeOrderBook(
            @PathVariable String instId,
            @RequestHeader(value = "X-Session-Token", required = false) String token) {

        String username;
        try {
            username = sessionService.validate(token);
        } catch (SecurityException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }

        String pair = instId.toUpperCase();
        log.info("DELETE /api/market/orderbook/{} by user={}", pair, username);
        wsManager.unsubscribeOrderBook(pair);

        return ResponseEntity.ok(Map.of("status", "unsubscribed", "instId", pair));
    }
}
