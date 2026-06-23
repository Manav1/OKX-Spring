package com.okx.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Generic OKX REST API response wrapper.
 * OKX always returns: { "code": "0", "msg": "", "data": [...] }
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OkxApiResponse<T> {

    /** "0" means success; any other value is an error code */
    @JsonProperty("code")
    private String code;

    /** Error message (empty on success) */
    @JsonProperty("msg")
    private String msg;

    /** Payload */
    @JsonProperty("data")
    private List<T> data;

    public boolean isSuccess() {
        return "0".equals(code);
    }
}
