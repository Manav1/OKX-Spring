package com.okx.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Ticker {

    @JsonProperty("instId")
    private String instId;

    @JsonProperty("instType")
    private String instType;

    @JsonProperty("last")
    private String lastPrice;

    @JsonProperty("lastSz")
    private String lastSize;

    @JsonProperty("askPx")
    private String askPrice;

    @JsonProperty("askSz")
    private String askSize;

    @JsonProperty("bidPx")
    private String bidPrice;

    @JsonProperty("bidSz")
    private String bidSize;

    @JsonProperty("open24h")
    private String open24h;

    @JsonProperty("high24h")
    private String high24h;

    @JsonProperty("low24h")
    private String low24h;

    @JsonProperty("vol24h")
    private String volume24h;

    @JsonProperty("volCcy24h")
    private String volumeCcy24h;

    @JsonProperty("ts")
    private String timestamp;

    /**
     * Safely parses a string to BigDecimal.
     * Returns null for null, empty, or non-numeric strings
     * so the UI shows "—" rather than crashing.
     */
    private BigDecimal parse(String val) {
        if (val == null || val.isBlank()) return null;
        try { return new BigDecimal(val); }
        catch (NumberFormatException e) { return null; }
    }

    /**
     * 24h price change %: ((last - open24h) / open24h) * 100
     * Returns null if either value is missing or open24h is zero.
     */
    public BigDecimal getChange24hPct() {
        BigDecimal last = parse(lastPrice);
        BigDecimal open = parse(open24h);
        if (last == null || open == null || open.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return last
                .subtract(open)
                .divide(open, 6, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getVolumeCcy24hParsed() {
        BigDecimal v = parse(volumeCcy24h);
        return v != null ? v : BigDecimal.ZERO;
    }
}