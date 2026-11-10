package org.pdi.web.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

/**
 * DIGIT request envelope header. Passed through to HCM services unchanged.
 */
@Validated
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RequestInfo {

    @JsonProperty("apiId")
    private String apiId;

    @JsonProperty("ver")
    private String ver;

    @JsonProperty("ts")
    private Long ts;

    @JsonProperty("action")
    private String action;

    @JsonProperty("did")
    private String did;

    @JsonProperty("key")
    private String key;

    @JsonProperty("msgId")
    private String msgId;

    @JsonProperty("authToken")
    private String authToken;

    @JsonProperty("userInfo")
    private UserInfo userInfo;
}
