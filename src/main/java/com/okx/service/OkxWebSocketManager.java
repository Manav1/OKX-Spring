package com.okx.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.okx.websocket.OkxWebSocketClient;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class OkxWebSocketManager {

    @Value("${okx.websocket.public-url}")
    private String okxWsUrl;

    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    private final Map<String, OkxWebSocketClient> activeClients = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> healthCheckTask;

    public void subscribeOrderBook(String instId) {
        String pair = instId.toUpperCase();
        if (activeClients.containsKey(pair)) {
            log.info("Already subscribed to order book for {}", pair);
            return;
        }

        OkxWebSocketClient client = new OkxWebSocketClient(
                URI.create(okxWsUrl),
                pair,
                OkxWebSocketClient.CHANNEL_ORDER_BOOK,
                message -> {
                    if (activeClients.containsKey(pair)) {
                        broadcast("/topic/orderbook/" + pair, message);
                    } else {
                        log.debug("Dropping message for unsubscribed pair {}", pair);
                    }
                },
                objectMapper
        );

        try {
            client.connectBlocking(10, TimeUnit.SECONDS);
            activeClients.put(pair, client);
            ensureHealthCheck();
            log.info("Subscribed to order book for {}", pair);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while connecting for {}", pair);
        } catch (Exception e) {
            log.error("Failed to connect for {}", pair, e);
        }
    }

    public void unsubscribeOrderBook(String instId) {
        String pair = instId.toUpperCase();

        OkxWebSocketClient client = activeClients.remove(pair);

        if (client == null) {
            log.warn("No active subscription found for {}", pair);
            return;
        }

        try {
            client.unsubscribe();
            client.closeBlocking();
            log.info("Unsubscribed and closed OKX WS connection for {}", pair);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while closing WS for {}", pair);
        }
    }

    private void broadcast(String stompTopic, String rawJson) {
        try {
            Object parsed = objectMapper.readValue(rawJson, Object.class);
            messagingTemplate.convertAndSend(stompTopic, parsed);
        } catch (Exception e) {
            messagingTemplate.convertAndSend(stompTopic, rawJson);
        }
    }

    private void ensureHealthCheck() {
        if (healthCheckTask == null || healthCheckTask.isDone()) {
            healthCheckTask = scheduler.scheduleAtFixedRate(() ->
                            activeClients.forEach((instId, client) -> {
                                if (!client.isOpen()) {
                                    log.warn("Reconnecting dropped WS for {}", instId);
                                    activeClients.remove(instId);
                                    subscribeOrderBook(instId);
                                }
                            }),
                    30, 30, TimeUnit.SECONDS);
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down — closing {} OKX connections", activeClients.size());
        activeClients.forEach((pair, client) -> {
            client.unsubscribe();
            client.close();
        });
        activeClients.clear();
        scheduler.shutdownNow();
    }
}