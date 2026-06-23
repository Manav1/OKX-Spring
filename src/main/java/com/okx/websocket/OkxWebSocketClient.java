package com.okx.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.okx.model.WsSubscription;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.List;
import java.util.function.Consumer;

/**
 * Low-level WebSocket client that maintains a persistent connection to OKX.
 *
 * Subscribes to the OKX public WebSocket and routes messages to a listener.
 *
 * Supported channels (per OKX docs):
 *   - "books5"  → Top-5 order book levels, pushed on every change (~100ms)
 *   - "books"   → Full 400-level order book (heavier)
 *   - "tickers" → Best bid/ask + last price ticker
 *
 * Keep-alive: OKX sends a plain-text "ping" every 30s; we reply with "pong".
 */
@Slf4j
public class OkxWebSocketClient extends WebSocketClient {

    /** OKX channel name — "books5" for top-5 order book */
    public static final String CHANNEL_ORDER_BOOK = "books";

    private final ObjectMapper objectMapper;
    private final String instId;
    private final String channel;
    private final Consumer<String> messageListener;

    public OkxWebSocketClient(URI serverUri,
                              String instId,
                              String channel,
                              Consumer<String> messageListener,
                              ObjectMapper objectMapper) {
        super(serverUri);
        this.instId          = instId;
        this.channel         = channel;
        this.messageListener = messageListener;
        this.objectMapper    = objectMapper;
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        log.info("Connected to OKX WebSocket — channel={}, instId={}, HTTP={}",
                channel, instId, handshake.getHttpStatus());
        subscribe();
    }

    @Override
    public void onMessage(String message) {
        // OKX keep-alive: plain text "ping" → reply "pong"
        if ("ping".equalsIgnoreCase(message.trim())) {
            log.debug("OKX ping → pong");
            send("pong");
            return;
        }
        log.debug("OKX WS [{}][{}]: {}", channel, instId, message);
        messageListener.accept(message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        log.warn("OKX WebSocket closed — channel={}, instId={}, code={}, reason='{}', remote={}",
                channel, instId, code, reason, remote);
    }

    @Override
    public void onError(Exception ex) {
        log.error("OKX WebSocket error — channel={}, instId={}", channel, instId, ex);
    }

    private void subscribe() {
        try {
            WsSubscription sub = new WsSubscription(
                    "subscribe",
                    List.of(new WsSubscription.Arg(channel, instId))
            );
            String json = objectMapper.writeValueAsString(sub);
            log.info("Subscribing → {}", json);
            send(json);
        } catch (Exception e) {
            log.error("Failed to send subscribe message", e);
        }
    }

    public void unsubscribe() {
        try {
            WsSubscription unsub = new WsSubscription(
                    "unsubscribe",
                    List.of(new WsSubscription.Arg(channel, instId))
            );
            send(objectMapper.writeValueAsString(unsub));
            log.info("Unsubscribed from channel={} instId={}", channel, instId);
        } catch (Exception e) {
            log.warn("Failed to send unsubscribe", e);
        }
    }

    public String getInstId()  { return instId; }
    public String getChannel() { return channel; }
}