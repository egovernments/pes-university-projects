package org.pdi.web.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

/**
 * DIGIT response envelope header.
 */
@Validated
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ResponseInfo {

    @JsonProperty("apiId")
    private String apiId;

    @JsonProperty("ver")
    private String ver;

    @JsonProperty("ts")
    private Long ts;

    @JsonProperty("resMsgId")
    private String resMsgId;

    @JsonProperty("msgId")
    private String msgId;

    @JsonProperty("status")
    private String status;
}
