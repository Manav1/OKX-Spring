package com.okx.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.okx.model.OkxApiResponse;
import com.okx.model.Ticker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OkxRestService {

    @Value("${okx.rest.base-url}")
    private String baseUrl;

    @Value("${okx.rest.tickers-path}")
    private String tickersPath;

    @Value("${okx.top-instruments.count:20}")
    private int topCount;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public List<Ticker> getTopTradedPairs() {
        String url = baseUrl + tickersPath + "?instType=SPOT";
        log.info("Calling OKX REST: {}", url);

        try {
            String rawJson = restTemplate.getForObject(url, String.class);
            OkxApiResponse<Ticker> response = objectMapper.readValue(
                    rawJson, new TypeReference<OkxApiResponse<Ticker>>() {});

            if (!response.isSuccess()) {
                log.error("OKX API error — code: {}, msg: {}", response.getCode(), response.getMsg());
                return Collections.emptyList();
            }

            List<Ticker> tickers = response.getData();
            log.info("Total SPOT pairs from OKX: {}", tickers.size());

            return tickers.stream()
                    .filter(t -> t.getVolumeCcy24hParsed() != null)
                    .sorted(Comparator.comparing(Ticker::getVolumeCcy24hParsed).reversed())
                    .limit(topCount)
                    .toList();

        } catch (Exception e) {
            log.error("Failed to fetch tickers from OKX", e);
            return Collections.emptyList();
        }
    }

}