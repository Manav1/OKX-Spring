package com.okx.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WsSubscription {

    private String op;         // "subscribe" | "unsubscribe"
    private List<Arg> args;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Arg {
        private String channel;   // "tickers"
        private String instId;    // "BTC-USDT"
    }
}
